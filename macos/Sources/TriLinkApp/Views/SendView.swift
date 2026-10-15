import SwiftUI
import UniformTypeIdentifiers
import TriLinkCore

struct SendView: View {
    @EnvironmentObject var state: AppStateManager
    @State private var isTargeted: Bool = false
    @State private var selectedFileURL: URL? = nil
    @State private var textInput: String = ""
    @State private var isSendingText: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Target Device Selector
                VStack(alignment: .leading, spacing: 8) {
                    Text("Select Recipient Device")
                        .font(.headline)

                    if state.trustedDevices.isEmpty {
                        HStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.orange)
                                .font(.title2)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("No Paired Devices")
                                    .fontWeight(.medium)
                                Text("Pair your Realme phone or Xiaomi Pad in the Devices tab first.")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Button("Pair Now") {
                                state.selectedTab = .devices
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding()
                        .background(Color(NSColor.controlBackgroundColor))
                        .cornerRadius(10)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(state.trustedDevices) { device in
                                    DeviceCard(
                                        device: device,
                                        isSelected: state.selectedPeerId == device.id,
                                        onSelect: {
                                            state.selectedPeerId = device.id
                                        }
                                    )
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }

                Divider()

                // File Drag & Drop Target Zone
                VStack(alignment: .leading, spacing: 8) {
                    Text("Send Files")
                        .font(.headline)

                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(
                                isTargeted ? Color.accentColor : Color.secondary.opacity(0.3),
                                style: StrokeStyle(lineWidth: 2, dash: [8])
                            )
                            .background(isTargeted ? Color.accentColor.opacity(0.05) : Color(NSColor.controlBackgroundColor).opacity(0.5))

                        VStack(spacing: 12) {
                            Image(systemName: isTargeted ? "arrow.down.doc.fill" : "doc.badge.plus")
                                .font(.system(size: 40))
                                .foregroundColor(isTargeted ? .accentColor : .secondary)

                            if let url = selectedFileURL {
                                VStack(spacing: 4) {
                                    Text(url.lastPathComponent)
                                        .font(.headline)
                                    Text("Ready to send")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            } else {
                                VStack(spacing: 4) {
                                    Text("Drag and drop files here")
                                        .font(.headline)
                                    Text("or click Choose File to browse")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }

                            HStack(spacing: 12) {
                                Button("Choose File...") {
                                    chooseFile()
                                }
                                .buttonStyle(.bordered)

                                if selectedFileURL != nil {
                                    Button("Send File") {
                                        sendSelectedFile()
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(selectedTargetDevice == nil)
                                }
                            }
                        }
                        .padding(32)
                    }
                    .frame(minHeight: 200)
                    .onDrop(of: [.fileURL], isTargeted: $isTargeted) { providers in
                        handleDrop(providers: providers)
                    }
                }

                Divider()

                // Quick Text / Link Share Zone
                VStack(alignment: .leading, spacing: 8) {
                    Text("Quick Text or Link Share")
                        .font(.headline)

                    HStack(spacing: 12) {
                        TextField("Type a message or paste a URL to send instantly...", text: $textInput)
                            .textFieldStyle(.roundedBorder)

                        Button("Send") {
                            sendQuickText()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(textInput.trimmingCharacters(in: .whitespaces).isEmpty || selectedTargetDevice == nil)
                    }
                }
            }
            .padding(24)
        }
    }

    private var selectedTargetDevice: Device? {
        state.trustedDevices.first(where: { $0.id == state.selectedPeerId }) ?? state.trustedDevices.first
    }

    private func chooseFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            self.selectedFileURL = url
        }
    }

    private func handleDrop(providers: [NSItemProvider]) -> Bool {
        guard let provider = providers.first else { return false }
        provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
            if let data = item as? Data, let url = URL(dataRepresentation: data, relativeTo: nil) {
                DispatchQueue.main.async {
                    self.selectedFileURL = url
                }
            } else if let url = item as? URL {
                DispatchQueue.main.async {
                    self.selectedFileURL = url
                }
            }
        }
        return true
    }

    private func sendSelectedFile() {
        guard let url = selectedFileURL, let target = selectedTargetDevice else { return }
        state.sendFile(to: target, fileURL: url)
        self.selectedFileURL = nil
        state.selectedTab = .transfers
    }

    private func sendQuickText() {
        let text = textInput.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty, let target = selectedTargetDevice else { return }
        let isUrl = text.starts(with: "http://") || text.starts(with: "https://")
        state.sendText(to: target, text: text, isUrl: isUrl)
        self.textInput = ""
        state.selectedTab = .transfers
    }
}

struct DeviceCard: View {
    let device: Device
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: device.platform == "macos" ? "laptopcomputer" : "phone.android")
                        .font(.title2)
                        .foregroundColor(isSelected ? .accentColor : .primary)
                    Spacer()
                    Circle()
                        .fill(device.isOnlineLocally ? Color.green : Color.secondary)
                        .frame(width: 8, height: 8)
                }

                Text(device.deviceName)
                    .font(.headline)
                    .lineLimit(1)

                Text(device.platform.capitalized)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(12)
            .frame(width: 160)
            .background(Color(NSColor.controlBackgroundColor))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}
