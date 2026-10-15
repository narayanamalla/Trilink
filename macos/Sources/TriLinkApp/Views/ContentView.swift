import SwiftUI

struct ContentView: View {
    @EnvironmentObject var state: AppStateManager

    var body: some View {
        NavigationSplitView {
            List(NavigationTab.allCases, selection: $state.selectedTab) { tab in
                NavigationLink(value: tab) {
                    Label(tab.rawValue, systemImage: tab.iconName)
                }
            }
            .navigationSplitViewColumnWidth(min: 180, ideal: 200, max: 240)
            .listStyle(.sidebar)
            .safeAreaInset(edge: .bottom) {
                // Bottom device status pill
                VStack(spacing: 4) {
                    Divider()
                    HStack(spacing: 8) {
                        Circle()
                            .fill(state.isServerRunning ? Color.green : Color.red)
                            .frame(width: 8, height: 8)
                        Text(state.localDevice.deviceName)
                            .font(.caption)
                            .fontWeight(.medium)
                            .lineLimit(1)
                        Spacer()
                        Text(":\(state.port)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
            }
        } detail: {
            VStack(spacing: 0) {
                // Status banner if present
                if let banner = state.statusBanner {
                    HStack {
                        Image(systemName: "info.circle.fill")
                            .foregroundColor(.accentColor)
                        Text(banner)
                            .font(.subheadline)
                        Spacer()
                        Button(action: { state.statusBanner = nil }) {
                            Image(systemName: "xmark")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.accentColor.opacity(0.1))
                }

                // Main Tab Content
                switch state.selectedTab {
                case .send:
                    SendView()
                case .transfers:
                    TransfersView()
                case .devices:
                    DevicesView()
                case .settings:
                    SettingsView()
                }
            }
            .navigationTitle("TriLink — \(state.selectedTab.rawValue)")
            .toolbar {
                ToolbarItem(placement: .automatic) {
                    Button(action: {
                        state.openDownloadsFolder()
                    }) {
                        Label("Downloads", systemImage: "folder")
                    }
                    .help("Open Downloads Folder")
                }
            }
        }
    }
}
