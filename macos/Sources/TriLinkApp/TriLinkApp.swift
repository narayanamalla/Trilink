import Foundation
import AppKit
import TriLinkCore

final class AppDelegate: NSObject, NSApplicationDelegate, LocalTransferServerDelegate, @unchecked Sendable {
    private var statusItem: NSStatusItem?
    private var server: LocalTransferServer?
    private var downloadsDir: URL!

    func applicationDidFinishLaunching(_ notification: Notification) {
        let userDownloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        downloadsDir = userDownloads.appendingPathComponent("TriLink")
        try? FileManager.default.createDirectory(at: downloadsDir, withIntermediateDirectories: true)

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem?.button {
            button.title = "⚡ TriLink"
        }

        updateMenu(status: "Active (Port 42425)")

        let security = SecurityManager()
        let deviceId = UUID().uuidString
        let srv = LocalTransferServer(port: 42425, downloadsDirectory: downloadsDir)
        srv.delegate = self
        self.server = srv

        let localDevice = Device(
            id: deviceId,
            deviceName: Host.current().localizedName ?? "MacBook Air",
            platform: "macos",
            publicKey: security.publicSigningKeyBase64,
            exchangeKey: security.publicAgreementKeyBase64,
            localEndpoint: "http://127.0.0.1:42425",
            isOnlineLocally: true
        )

        srv.trustedDevicesProvider = {
            [localDevice.id: localDevice]
        }

        do {
            try srv.start()
            print("✓ TriLink Menu Bar App active on port 42425")
        } catch {
            print("Server start error: \(error)")
            updateMenu(status: "Port 42425 Busy")
        }
    }

    private func updateMenu(status: String) {
        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "⚡ TriLink: \(status)", action: nil, keyEquivalent: ""))
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

    @objc private func openDownloads() {
        if let dir = downloadsDir {
            NSWorkspace.shared.open(dir)
        }
    }

    @objc private func quitApp() {
        NSApplication.shared.terminate(nil)
    }

    func serverDidReceivePairingRequest(_ payload: PairingPayload) async -> Bool {
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
        print("✓ Received: \(displayName)")
    }

    func serverDidReceiveCancellation(transferId: String) async {}
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
