import SwiftUI
import AppKit
import TriLinkCore

struct DevicesView: View {
    @EnvironmentObject var state: AppStateManager
    @State private var manualPeerInput: String = ""
    @State private var copied: Bool = false
    @State private var editingDeviceId: String? = nil
    @State private var editingIpText: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // This Mac Banner & QR Code
                HStack(alignment: .top, spacing: 24) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Pairing with Android")
                            .font(.title2)
                            .fontWeight(.bold)

                        Text("Scan this QR code with the TriLink app on your Realme phone or Xiaomi Pad, or copy the pairing code below.")
                            .font(.body)
                            .foregroundColor(.secondary)

                        HStack(spacing: 8) {
                            Circle()
                                .fill(state.isServerRunning ? Color.green : Color.red)
                                .frame(width: 10, height: 10)
                            Text("Local Endpoint: http://\(state.localIP):\(state.port)")
                                .font(.subheadline)
                                .fontWeight(.medium)
                        }

                        Text("Identity: \(state.localDevice.publicKey.prefix(16))...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .textSelection(.enabled)

                        Spacer()

                        Button(action: {
                            state.copyPairingPayload()
                            copied = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                copied = false
                            }
                        }) {
                            HStack {
                                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                                Text(copied ? "Copied!" : "Copy Pairing Code")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    Spacer()

                    // QR Code Container
                    VStack {
                        if let qrImg = state.generatePairingQRImage() {
                            Image(nsImage: qrImg)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 160, height: 160)
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(12)
                                .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                        } else {
                            ProgressView()
                                .frame(width: 160, height: 160)
                        }
                        Text("Single-use pairing code")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color(NSColor.controlBackgroundColor))
                .cornerRadius(12)

                Divider()

                // Manual Pairing Input
                VStack(alignment: .leading, spacing: 8) {
                    Text("Manual Pair with Phone")
                        .font(.headline)

                    Text("If you cannot scan the QR code, paste your Android device's pairing code or IP address here:")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    HStack(spacing: 12) {
                        TextField("Paste pairing code or phone IP (e.g. 192.168.0.15:42425)", text: $manualPeerInput)
                            .textFieldStyle(.roundedBorder)

                        Button("Connect & Pair") {
                            state.pairWithPeer(input: manualPeerInput)
                            manualPeerInput = ""
                        }
                        .buttonStyle(.bordered)
                        .disabled(manualPeerInput.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }

                Divider()

                // Trusted Devices List
                VStack(alignment: .leading, spacing: 12) {
                    Text("Trusted Devices (\(state.trustedDevices.count))")
                        .font(.headline)

                    if state.trustedDevices.isEmpty {
                        Text("No trusted devices paired yet. Follow the steps above to pair your first phone or tablet.")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .padding(.vertical, 8)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(state.trustedDevices) { device in
                                HStack(spacing: 16) {
                                    Image(systemName: device.platform == "macos" ? "laptopcomputer" : "phone.android")
                                        .font(.title2)
                                        .frame(width: 36)

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(device.deviceName)
                                            .font(.headline)

                                        let hasEndpoint = !(device.localEndpoint?.trimmingCharacters(in: .whitespaces).isEmpty ?? true)
                                        let epDisplay = hasEndpoint ? device.localEndpoint! : "No local endpoint"

                                        HStack(spacing: 6) {
                                            Circle()
                                                .fill(hasEndpoint ? Color.green : Color.orange)
                                                .frame(width: 8, height: 8)
                                            Text("Platform: \(device.platform.capitalized) • Endpoint: \(epDisplay)")
                                                .font(.caption)
                                                .foregroundColor(hasEndpoint ? .secondary : .orange)
                                        }

                                        if editingDeviceId == device.id {
                                            HStack(spacing: 8) {
                                                TextField("e.g. 192.168.1.15:42426", text: $editingIpText)
                                                    .textFieldStyle(.roundedBorder)
                                                    .frame(maxWidth: 220)
                                                Button("Save") {
                                                    state.updateDeviceEndpoint(deviceId: device.id, newEndpoint: editingIpText)
                                                    editingDeviceId = nil
                                                }
                                                .buttonStyle(.borderedProminent)
                                                .controlSize(.small)
                                                Button("Cancel") {
                                                    editingDeviceId = nil
                                                }
                                                .buttonStyle(.bordered)
                                                .controlSize(.small)
                                            }
                                            .padding(.top, 4)
                                        }
                                    }

                                    Spacer()

                                    if editingDeviceId != device.id {
                                        Button(action: {
                                            editingIpText = device.localEndpoint ?? "http://192.168.1.:42426"
                                            editingDeviceId = device.id
                                        }) {
                                            HStack(spacing: 4) {
                                                Image(systemName: "pencil")
                                                Text(device.localEndpoint?.isEmpty == false ? "Edit IP" : "Set Phone IP")
                                            }
                                            .font(.caption)
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.small)
                                    }

                                    Button(role: .destructive) {
                                        state.revokeDevice(id: device.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundColor(.red)
                                    }
                                    .buttonStyle(.borderless)
                                }
                                .padding()
                                .background(Color(NSColor.controlBackgroundColor))
                                .cornerRadius(8)
                            }
                        }
                    }
                }
            }
            .padding(24)
        }
    }
}
