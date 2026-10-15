import Foundation
import SwiftUI
import AppKit
import CoreImage
import TriLinkCore

public enum NavigationTab: String, CaseIterable, Identifiable {
    case send = "Send"
    case transfers = "Transfers"
    case devices = "Devices & Pairing"
    case settings = "Settings"

    public var id: String { rawValue }

    public var iconName: String {
        switch self {
        case .send: return "arrow.up.circle.fill"
        case .transfers: return "arrow.down.circle.fill"
        case .devices: return "laptopcomputer.and.iphone"
        case .settings: return "gearshape.fill"
        }
    }
}

final class ThreadSafeDeviceBox: @unchecked Sendable {
    private let lock = NSLock()
    private var devices: [String: Device] = [:]

    func set(_ device: Device) {
        lock.lock()
        devices[device.id] = device
        lock.unlock()
    }

    func remove(_ id: String) {
        lock.lock()
        devices.removeValue(forKey: id)
        lock.unlock()
    }

    func getAll() -> [String: Device] {
        lock.lock()
        defer { lock.unlock() }
        return devices
    }
}

@MainActor
public final class AppStateManager: ObservableObject, LocalTransferServerDelegate, @unchecked Sendable {
    @Published public var selectedTab: NavigationTab = .send
    @Published public var isServerRunning: Bool = false
    @Published public var localIP: String = "127.0.0.1"
    @Published public var port: UInt16 = 42425
    @Published public var downloadsDirectory: URL
    @Published public var localDevice: Device
    @Published public var trustedDevices: [Device] = []
    @Published public var transfers: [Transfer] = []
    @Published public var selectedPeerId: String? = nil
    @Published public var statusBanner: String? = nil

    private var server: LocalTransferServer?
    private let security = SecurityManager()
    private let client: LocalTransferClient
    private let deviceBox = ThreadSafeDeviceBox()

    public init() {
        let userDownloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        let dlDir = userDownloads.appendingPathComponent("TriLink")
        try? FileManager.default.createDirectory(at: dlDir, withIntermediateDirectories: true)
        self.downloadsDirectory = dlDir

        let devId = UUID().uuidString
        self.client = LocalTransferClient(securityManager: security, localDeviceId: devId)

        let initialDevice = Device(
            id: devId,
            deviceName: Host.current().localizedName ?? "MacBook Air",
            platform: "macos",
            publicKey: security.publicSigningKeyBase64,
            exchangeKey: security.publicAgreementKeyBase64,
            localEndpoint: "http://127.0.0.1:42425",
            isOnlineLocally: true
        )
        self.localDevice = initialDevice
        self.deviceBox.set(initialDevice)

        refreshIP()
        startServer()
    }

    public func refreshIP() {
        let ip = getLocalIPAddress()
        self.localIP = ip
        let updatedDevice = Device(
            id: localDevice.id,
            deviceName: localDevice.deviceName,
            platform: "macos",
            publicKey: security.publicSigningKeyBase64,
            exchangeKey: security.publicAgreementKeyBase64,
            localEndpoint: "http://\(ip):\(port)",
            isOnlineLocally: true
        )
        self.localDevice = updatedDevice
        self.deviceBox.set(updatedDevice)
    }

    public func startServer() {
        server?.stop()
        let srv = LocalTransferServer(port: port, downloadsDirectory: downloadsDirectory)
        srv.delegate = self
        self.server = srv

        let box = self.deviceBox
        srv.trustedDevicesProvider = {
            return box.getAll()
        }

        do {
            try srv.start()
            self.isServerRunning = true
            self.statusBanner = "Server active on http://\(localIP):\(port)"
        } catch {
            self.isServerRunning = false
            self.statusBanner = "Port \(port) unavailable: \(error.localizedDescription)"
        }
    }

    public func copyPairingPayload() {
        let payload: [String: Any] = [
            "version": 1,
            "pairing_id": UUID().uuidString,
            "device_id": localDevice.id,
            "device_name": localDevice.deviceName,
            "platform": "macos",
            "public_key": localDevice.publicKey,
            "exchange_key": localDevice.exchangeKey,
            "local_endpoint": "http://\(localIP):\(port)",
            "expires_at": Int64(Date().timeIntervalSince1970) + 600
        ]

        if let data = try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted]),
           let str = String(data: data, encoding: .utf8) {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(str, forType: .string)
            self.statusBanner = "✓ Pairing JSON copied to clipboard!"
        }
    }

    public func generatePairingQRImage() -> NSImage? {
        let payloadString = "http://\(localIP):\(port)/pair?id=\(localDevice.id)&name=\(localDevice.deviceName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&key=\(localDevice.publicKey)"

        let context = CIContext()
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(Data(payloadString.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }

        let transform = CGAffineTransform(scaleX: 8, y: 8)
        let scaled = output.transformed(by: transform)
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return NSImage(cgImage: cg, size: NSSize(width: 200, height: 200))
    }

    public func pairWithPeer(input: String) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // If JSON
        if trimmed.starts(with: "{"), let data = trimmed.data(using: .utf8),
           let payload = try? JSONDecoder().decode(PairingPayload.self, from: data) {
            let dev = Device(
                id: payload.deviceId,
                deviceName: payload.deviceName,
                platform: payload.platform,
                publicKey: payload.publicKey,
                exchangeKey: payload.exchangeKey,
                localEndpoint: payload.localEndpoint,
                isOnlineLocally: true
            )
            addTrustedDevice(dev)

            if let ep = payload.localEndpoint, !ep.isEmpty {
                let myPayload = PairingPayload(
                    version: 1,
                    pairingId: UUID().uuidString,
                    deviceId: localDevice.id,
                    deviceName: localDevice.deviceName,
                    platform: "macos",
                    publicKey: localDevice.publicKey,
                    exchangeKey: localDevice.exchangeKey,
                    localEndpoint: "http://\(localIP):\(port)"
                )
                Task {
                    _ = try? await self.client.sendPairingRequest(endpoint: ep, payload: myPayload)
                }
            }

            self.statusBanner = "✓ Paired successfully with \(dev.deviceName)!"
            return
        }

        // If endpoint (e.g. 192.168.0.15:42425 or http://192.168.0.15:42425)
        let endpoint = trimmed.starts(with: "http") ? trimmed : "http://\(trimmed)"
        let pairingPayload = PairingPayload(
            version: 1,
            pairingId: UUID().uuidString,
            deviceId: localDevice.id,
            deviceName: localDevice.deviceName,
            platform: "macos",
            publicKey: localDevice.publicKey,
            exchangeKey: localDevice.exchangeKey,
            localEndpoint: "http://\(localIP):\(port)"
        )

        Task {
            do {
                let success = try await self.client.sendPairingRequest(endpoint: endpoint, payload: pairingPayload)
                if success {
                    let dev = Device(
                        id: UUID().uuidString,
                        deviceName: "Peer Device",
                        platform: "android",
                        publicKey: "",
                        exchangeKey: "",
                        localEndpoint: endpoint,
                        isOnlineLocally: true
                    )
                    self.addTrustedDevice(dev)
                    self.statusBanner = "✓ Handshake accepted by \(endpoint)!"
                } else {
                    self.statusBanner = "Pairing request was rejected by peer."
                }
            } catch {
                self.statusBanner = "Pairing failed: \(error.localizedDescription)"
            }
        }
    }

    public func addTrustedDevice(_ device: Device) {
        deviceBox.set(device)

        if let idx = trustedDevices.firstIndex(where: { $0.id == device.id }) {
            trustedDevices[idx] = device
        } else {
            trustedDevices.append(device)
        }

        if selectedPeerId == nil {
            selectedPeerId = device.id
        }
    }

    public func revokeDevice(id: String) {
        deviceBox.remove(id)

        trustedDevices.removeAll { $0.id == id }
        if selectedPeerId == id {
            selectedPeerId = trustedDevices.first?.id
        }
        self.statusBanner = "Device revoked."
    }

    public func sendFile(to targetDevice: Device, fileURL: URL) {
        guard let endpoint = targetDevice.localEndpoint, !endpoint.isEmpty else {
            self.statusBanner = "Target device has no reachable local IP."
            return
        }

        let transferId = UUID().uuidString
        let displayName = fileURL.lastPathComponent
        let size = (try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0

        let transfer = Transfer(
            id: transferId,
            senderDeviceId: localDevice.id,
            recipientDeviceId: targetDevice.id,
            itemType: .file,
            displayName: displayName,
            sizeBytes: Int64(size),
            sha256: "",
            deliveryMode: .local,
            status: .uploading,
            localPath: fileURL.path
        )
        self.transfers.insert(transfer, at: 0)

        Task {
            do {
                try await self.client.transferFile(
                    endpoint: endpoint,
                    transferId: transferId,
                    targetDeviceId: targetDevice.id,
                    fileURL: fileURL,
                    displayName: displayName,
                    itemType: .file
                ) { sent, total in
                    DispatchQueue.main.async { [weak self] in
                        guard let self = self else { return }
                        if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                            self.transfers[idx].bytesTransferred = sent
                            if sent >= total {
                                self.transfers[idx].status = .delivered
                            }
                        }
                    }
                }
                self.statusBanner = "✓ Transferred \(displayName) to \(targetDevice.deviceName)!"
            } catch {
                if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                    self.transfers[idx].status = .failed
                    self.transfers[idx].failureReason = error.localizedDescription
                }
                self.statusBanner = "Transfer failed: \(error.localizedDescription)"
            }
        }
    }

    public func sendText(to targetDevice: Device, text: String, isUrl: Bool) {
        guard let endpoint = targetDevice.localEndpoint, !endpoint.isEmpty else {
            self.statusBanner = "Target device has no reachable local IP."
            return
        }

        let transferId = UUID().uuidString
        let displayName = isUrl ? "Shared URL" : "Shared Text"
        let transfer = Transfer(
            id: transferId,
            senderDeviceId: localDevice.id,
            recipientDeviceId: targetDevice.id,
            itemType: isUrl ? .url : .text,
            displayName: displayName,
            sizeBytes: Int64(text.utf8.count),
            sha256: "",
            deliveryMode: .local,
            status: .uploading,
            textContent: text
        )
        self.transfers.insert(transfer, at: 0)

        Task {
            do {
                try await self.client.sendText(
                    endpoint: endpoint,
                    transferId: transferId,
                    targetDeviceId: targetDevice.id,
                    text: text,
                    isUrl: isUrl
                )
                if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                    self.transfers[idx].status = .delivered
                }
                self.statusBanner = "✓ Sent to \(targetDevice.deviceName)!"
            } catch {
                if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                    self.transfers[idx].status = .failed
                    self.transfers[idx].failureReason = error.localizedDescription
                }
                self.statusBanner = "Send failed: \(error.localizedDescription)"
            }
        }
    }

    public func openFileInFinder(path: String) {
        NSWorkspace.shared.selectFile(path, inFileViewerRootedAtPath: "")
    }

    public func openDownloadsFolder() {
        NSWorkspace.shared.open(downloadsDirectory)
    }

    // LocalTransferServerDelegate
    public func serverDidReceivePairingRequest(_ payload: PairingPayload) async -> Bool {
        let dev = Device(
            id: payload.deviceId,
            deviceName: payload.deviceName,
            platform: payload.platform,
            publicKey: payload.publicKey,
            exchangeKey: payload.exchangeKey,
            localEndpoint: payload.localEndpoint,
            isOnlineLocally: true
        )
        addTrustedDevice(dev)

        DispatchQueue.main.async {
            self.statusBanner = "✓ Paired with \(dev.deviceName) (\(dev.platform))"
            let n = NSUserNotification()
            n.title = "TriLink Paired"
            n.informativeText = "Paired with \(dev.deviceName)"
            NSUserNotificationCenter.default.deliver(n)
        }
        return true
    }

    public func serverDidReceiveTransferInitiate(_ request: TransferInitiateRequest) async -> Bool {
        let transfer = Transfer(
            id: request.transferId,
            senderDeviceId: request.senderDeviceId,
            recipientDeviceId: localDevice.id,
            itemType: request.itemType,
            displayName: request.displayName,
            sizeBytes: request.sizeBytes,
            sha256: request.sha256,
            deliveryMode: .local,
            status: .downloading,
            textContent: request.textContent
        )
        DispatchQueue.main.async {
            self.transfers.insert(transfer, at: 0)
        }
        return true
    }

    public func serverDidCompleteTransfer(
        transferId: String,
        fileURL: URL,
        itemType: ItemType,
        displayName: String,
        textContent: String?
    ) async {
        DispatchQueue.main.async {
            if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                self.transfers[idx].status = .delivered
                self.transfers[idx].localPath = fileURL.path
                self.transfers[idx].bytesTransferred = self.transfers[idx].sizeBytes
            }
            self.statusBanner = "✓ Received \(displayName)"

            let n = NSUserNotification()
            n.title = "TriLink Received File"
            n.informativeText = "Saved \(displayName) to Downloads/TriLink"
            NSUserNotificationCenter.default.deliver(n)
        }
    }

    public func serverDidReceiveCancellation(transferId: String) async {
        DispatchQueue.main.async {
            if let idx = self.transfers.firstIndex(where: { $0.id == transferId }) {
                self.transfers[idx].status = .cancelled
            }
        }
    }

    private func getLocalIPAddress() -> String {
        var address: String = "127.0.0.1"
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return address }
        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let flags = Int32(ptr.pointee.ifa_flags)
            let addr = ptr.pointee.ifa_addr.pointee
            if (flags & (IFF_UP | IFF_RUNNING | IFF_LOOPBACK)) == (IFF_UP | IFF_RUNNING) {
                if addr.sa_family == UInt8(AF_INET) {
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    if getnameinfo(ptr.pointee.ifa_addr, socklen_t(ptr.pointee.ifa_addr.pointee.sa_len),
                                   &hostname, socklen_t(hostname.count),
                                   nil, 0, NI_NUMERICHOST) == 0 {
                        let ip = String(cString: hostname)
                        if !ip.starts(with: "127.") {
                            address = ip
                            break
                        }
                    }
                }
            }
        }
        return address
    }
}
