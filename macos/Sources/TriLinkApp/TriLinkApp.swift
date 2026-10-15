import Foundation
import TriLinkCore

@main
struct TriLinkMain {
    static func main() async {
        print("Starting TriLink macOS Companion...")

        let security = SecurityManager()
        let deviceId = UUID().uuidString
        let server = LocalTransferServer(port: 42425)

        let localDevice = Device(
            id: deviceId,
            deviceName: Host.current().localizedName ?? "MacBook Air",
            platform: "macos",
            publicKey: security.publicSigningKeyBase64,
            exchangeKey: security.publicAgreementKeyBase64,
            localEndpoint: "http://127.0.0.1:42425",
            isOnlineLocally: true
        )

        server.trustedDevicesProvider = {
            [localDevice.id: localDevice]
        }

        do {
            try server.start()
            print("✓ LocalTransferServer listening on port 42425")
            print("✓ Device ID: \(deviceId)")
            print("✓ Identity Fingerprint: \(security.publicSigningKeyBase64.prefix(16))...")
            print("TriLink Companion is running. Press Ctrl+C to terminate.")

            // Keep process running
            while true {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        } catch {
            print("Failed to start TriLink server: \(error)")
        }
    }
}
