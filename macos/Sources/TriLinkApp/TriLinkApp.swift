import Foundation
import AppKit
import TriLinkCore

final class AppDelegate: NSObject, NSApplicationDelegate, LocalTransferServerDelegate, @unchecked Sendable {
    private var statusItem: NSStatusItem?
    private var server: LocalTransferServer?
    private var downloadsDir: URL!
    private var security = SecurityManager()
    private var localDeviceId = UUID().uuidString
    private var trustedDevices: [String: Device] = [:]
    private let lock = NSLock()
    private var localIP = "127.0.0.1"

    func applicationDidFinishLaunching(_ notification: Notification) {
        let userDownloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        downloadsDir = userDownloads.appendingPathComponent("TriLink")
        try? FileManager.default.createDirectory(at: downloadsDir, withIntermediateDirectories: true)

        localIP = getLocalIPAddress()

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem?.button {
            button.title = "⚡ TriLink"
        }

        let srv = LocalTransferServer(port: 42425, downloadsDirectory: downloadsDir)
        srv.delegate = self
        self.server = srv

        let localDevice = Device(
            id: localDeviceId,
            deviceName: Host.current().localizedName ?? "MacBook Air",
            platform: "macos",
            publicKey: security.publicSigningKeyBase64,
            exchangeKey: security.publicAgreementKeyBase64,
            localEndpoint: "http://\(localIP):42425",
            isOnlineLocally: true
        )

        lock.lock()
        trustedDevices[localDeviceId] = localDevice
        lock.unlock()

        srv.trustedDevicesProvider = { [weak self] in
            guard let self = self else { return [:] }
            self.lock.lock()
            defer { self.lock.unlock() }
            return self.trustedDevices
        }

        do {
            try srv.start()
            print("✓ TriLink Menu Bar App active on http://\(localIP):42425")
            updateMenu(status: "Active (IP: \(localIP):42425)")
        } catch {
            print("Server start error: \(error)")
            updateMenu(status: "Port 42425 Busy")
        }
    }

    private func updateMenu(status: String) {
        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "⚡ TriLink: \(status)", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())

        lock.lock()
        let count = max(0, trustedDevices.count - 1)
        lock.unlock()
        menu.addItem(NSMenuItem(title: "📱 Paired Devices: \(count)", action: nil, keyEquivalent: ""))

        let copyItem = NSMenuItem(title: "📋 Copy Pairing Code for Phone", action: #selector(copyPairingCode), keyEquivalent: "c")
        copyItem.target = self
        menu.addItem(copyItem)

        menu.addItem(NSMenuItem.separator())
        let openItem = NSMenuItem(title: "📁 Open Downloads Folder", action: #selector(openDownloads), keyEquivalent: "o")
        openItem.target = self
        menu.addItem(openItem)

        menu.addItem(NSMenuItem.separator())
        let quitItem = NSMenuItem(title: "Quit TriLink", action: #selector(quitApp), keyEquivalent: "q")
        quitItem.target = self
        menu.addItem(quitItem)

        statusItem?.menu = menu
    }

    @objc private func copyPairingCode() {
        let payload: [String: Any] = [
            "version": 1,
            "pairing_id": UUID().uuidString,
            "device_id": localDeviceId,
            "device_name": Host.current().localizedName ?? "MacBook Air",
            "platform": "macos",
            "public_key": security.publicSigningKeyBase64,
            "exchange_key": security.publicAgreementKeyBase64,
            "local_endpoint": "http://\(localIP):42425",
            "expires_at": Int64(Date().timeIntervalSince1970) + 600
        ]

        if let data = try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted]),
           let str = String(data: data, encoding: .utf8) {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(str, forType: .string)

            let notification = NSUserNotification()
            notification.title = "TriLink Pairing Code Copied"
            notification.informativeText = "Paste this into your phone's TriLink app to pair!"
            NSUserNotificationCenter.default.deliver(notification)
        }
    }

    @objc private func openDownloads() {
        if let dir = downloadsDir {
            NSWorkspace.shared.open(dir)
        }
    }

    @objc private func quitApp() {
        NSApplication.shared.terminate(nil)
    }

    func serverDidReceivePairingRequest(_ payload: PairingPayload) async -> Bool {
        let newDevice = Device(
            id: payload.deviceId,
            deviceName: payload.deviceName,
            platform: payload.platform,
            publicKey: payload.publicKey,
            exchangeKey: payload.exchangeKey,
            localEndpoint: payload.localEndpoint,
            isOnlineLocally: true
        )

        lock.lock()
        trustedDevices[payload.deviceId] = newDevice
        lock.unlock()

        print("✓ Successfully paired with: \(payload.deviceName) (\(payload.platform))")
        DispatchQueue.main.async {
            self.updateMenu(status: "Active (IP: \(self.localIP):42425)")
            let notification = NSUserNotification()
            notification.title = "Device Paired Successfully"
            notification.informativeText = "Paired with \(payload.deviceName) (\(payload.platform))"
            NSUserNotificationCenter.default.deliver(notification)
        }
        return true
    }

    func serverDidReceiveTransferInitiate(_ request: TransferInitiateRequest) async -> Bool {
        return true
    }

    func serverDidCompleteTransfer(
        transferId: String,
        fileURL: URL,
        itemType: ItemType,
        displayName: String,
        textContent: String?
    ) async {
        print("✓ Received file: \(displayName)")
        DispatchQueue.main.async {
            let notification = NSUserNotification()
            notification.title = "TriLink File Received"
            notification.informativeText = "Saved \(displayName) to Downloads/TriLink"
            NSUserNotificationCenter.default.deliver(notification)
        }
    }

    func serverDidReceiveCancellation(transferId: String) async {}

    private func getLocalIPAddress() -> String {
        var address: String = "127.0.0.1"
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return address }
        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: firstAddr, by: { $0.pointee.ifa_next }) {
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

@main
struct TriLinkMain {
    static func main() {
        let app = NSApplication.shared
        let delegate = AppDelegate()
        app.delegate = delegate
        app.setActivationPolicy(.accessory)
        app.run()
    }
}
