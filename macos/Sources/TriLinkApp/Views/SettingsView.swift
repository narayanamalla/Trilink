import SwiftUI
import AppKit

struct SettingsView: View {
    @EnvironmentObject var state: AppStateManager
    @State private var portString: String = "42425"

    var body: some View {
        Form {
            Section(header: Text("Storage & Downloads").font(.headline)) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Incoming transfers are automatically saved to:")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    HStack {
                        Text(state.downloadsDirectory.path)
                            .font(.system(.body, design: .monospaced))
                            .lineLimit(1)
                            .truncationMode(.middle)
                            .padding(8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(NSColor.textBackgroundColor))
                            .cornerRadius(6)

                        Button("Change...") {
                            chooseDirectory()
                        }
                        .buttonStyle(.bordered)

                        Button("Reveal in Finder") {
                            state.openDownloadsFolder()
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(.vertical, 4)
            }

            Section(header: Text("Local Network & Security").font(.headline)) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Local Endpoint:")
                        Spacer()
                        Text("http://\(state.localIP):\(state.port)")
                            .font(.system(.body, design: .monospaced))
                            .foregroundColor(.secondary)
                    }

                    HStack {
                        Text("Server Status:")
                        Spacer()
                        HStack(spacing: 6) {
                            Circle()
                                .fill(state.isServerRunning ? Color.green : Color.red)
                                .frame(width: 8, height: 8)
                            Text(state.isServerRunning ? "Running" : "Stopped")
                                .fontWeight(.medium)
                        }
                    }

                    HStack {
                        Text("Security Keys:")
                        Spacer()
                        Text("Ed25519 (Signed) + X25519 (ECDH)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }

            Section(header: Text("About TriLink").font(.headline)) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("TriLink for macOS v1.0.0")
                        .fontWeight(.bold)
                    Text("Zero-trust, point-to-point local transfer between Android, macOS, and Linux.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 4)
            }
        }
        .formStyle(.grouped)
        .padding(16)
    }

    private func chooseDirectory() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.prompt = "Select Download Folder"
        if panel.runModal() == .OK, let url = panel.url {
            state.downloadsDirectory = url
            state.startServer()
        }
    }
}
