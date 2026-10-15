import SwiftUI
import AppKit

struct MenuBarView: View {
    @EnvironmentObject var state: AppStateManager

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("⚡ TriLink: \(state.isServerRunning ? "Active" : "Stopped")")
                .font(.headline)

            Text("Endpoint: http://\(state.localIP):\(state.port)")
                .font(.caption)
                .foregroundColor(.secondary)

            Divider()

            Text("Paired Devices: \(state.trustedDevices.count)")
                .font(.caption)

            Divider()

            Button("📋 Copy Pairing Code") {
                state.copyPairingPayload()
            }

            Button("📁 Open Downloads Folder") {
                state.openDownloadsFolder()
            }

            Divider()

            Button("Open TriLink Window") {
                NSApp.activate(ignoringOtherApps: true)
                if let window = NSApp.windows.first(where: { $0.canBecomeMain }) {
                    window.makeKeyAndOrderFront(nil)
                }
            }

            Button("Quit TriLink") {
                NSApp.terminate(nil)
            }
        }
    }
}
