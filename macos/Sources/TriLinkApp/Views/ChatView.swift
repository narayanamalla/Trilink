import SwiftUI
import AppKit
import TriLinkCore

struct ChatView: View {
    @EnvironmentObject var state: AppStateManager
    @State private var messageText: String = ""
    @State private var selectedDeviceId: String = ""

    private var activePeer: Device? {
        if let peer = state.trustedDevices.first(where: { $0.id == selectedDeviceId }) {
            return peer
        }
        return state.trustedDevices.first
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header bar
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Chat & Messages")
                        .font(.title2)
                        .fontWeight(.bold)

                    if let peer = activePeer {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(peer.isOnlineLocally ? Color.green : Color.orange)
                                .frame(width: 8, height: 8)
                            Text(peer.deviceName)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    } else {
                        Text("No devices paired yet")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                if state.trustedDevices.count > 1 {
                    Picker("Device", selection: $selectedDeviceId) {
                        ForEach(state.trustedDevices) { dev in
                            Text(dev.deviceName).tag(dev.id)
                        }
                    }
                    .pickerStyle(.menu)
                    .frame(width: 160)
                }
            }
            .padding()

            Divider()

            // Chat Feed
            if state.chatMessages.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.accentColor.opacity(0.8))
                    Text("No messages yet")
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text("Type a note or link below to start a continuous conversation with your phone or tablet.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 320)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(state.chatMessages) { msg in
                                ChatBubbleRow(
                                    message: msg,
                                    isOutgoing: msg.senderDeviceId == state.localDevice.id
                                )
                                .id(msg.id)
                            }
                        }
                        .padding()
                    }
                    .onReceive(state.$transfers) { _ in
                        if let lastId = state.chatMessages.last?.id {
                            withAnimation(.easeOut(duration: 0.2)) {
                                proxy.scrollTo(lastId, anchor: .bottom)
                            }
                        }
                    }
                    .onAppear {
                        if let lastId = state.chatMessages.last?.id {
                            proxy.scrollTo(lastId, anchor: .bottom)
                        }
                    }
                }
            }

            Divider()

            // Bottom Composer Bar
            HStack(spacing: 12) {
                TextField("Type a message or paste a link...", text: $messageText)
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(Color(NSColor.controlBackgroundColor))
                    .cornerRadius(8)
                    .onSubmit {
                        sendMessage()
                    }

                Button(action: sendMessage) {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 14, weight: .bold))
                        .padding(.horizontal, 6)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.regular)
                .disabled(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || activePeer == nil)
            }
            .padding(14)
            .background(Color(NSColor.windowBackgroundColor))
        }
        .onAppear {
            if selectedDeviceId.isEmpty, let firstId = state.trustedDevices.first?.id {
                selectedDeviceId = firstId
            }
        }
    }

    private func sendMessage() {
        let trimmed = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let peer = activePeer else { return }

        let isUrl = trimmed.starts(with: "http://") || trimmed.starts(with: "https://")
        state.sendText(to: peer, text: trimmed, isUrl: isUrl)
        messageText = ""
    }
}

struct ChatBubbleRow: View {
    let message: Transfer
    let isOutgoing: Bool

    @State private var isHovering: Bool = false

    var isUrl: Bool {
        message.itemType == .url || (message.textContent?.starts(with: "http://") == true || message.textContent?.starts(with: "https://") == true)
    }

    var body: some View {
        HStack {
            if isOutgoing { Spacer(minLength: 50) }

            VStack(alignment: isOutgoing ? .trailing : .leading, spacing: 4) {
                // Sender label for incoming
                if !isOutgoing {
                    Text(message.displayName == "Link" ? "Peer Link" : "Phone")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.secondary)
                        .padding(.leading, 4)
                }

                // Bubble
                HStack(spacing: 8) {
                    if isUrl {
                        Image(systemName: "link")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(isOutgoing ? .white.opacity(0.9) : .accentColor)
                    }

                    Text(message.textContent ?? message.displayName)
                        .font(.body)
                        .foregroundColor(isOutgoing ? .white : .primary)
                        .textSelection(.enabled)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(
                    isOutgoing
                        ? Color.accentColor
                        : Color(NSColor.controlBackgroundColor)
                )
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(isOutgoing ? Color.clear : Color.primary.opacity(0.08), lineWidth: 1)
                )

                // Footer with time, status, and actions
                HStack(spacing: 8) {
                    Text(formatTime(message.createdAt))
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    if isOutgoing {
                        Image(systemName: message.status == .delivered ? "checkmark.circle.fill" : "arrow.up.circle")
                            .font(.system(size: 10))
                            .foregroundColor(message.status == .delivered ? .green : .secondary)
                    }

                    if isHovering {
                        Button(action: copyText) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 10))
                        }
                        .buttonStyle(.plain)
                        .help("Copy message")

                        if isUrl, let urlStr = message.textContent, let url = URL(string: urlStr) {
                            Button(action: {
                                NSWorkspace.shared.open(url)
                            }) {
                                Image(systemName: "arrow.up.right.square")
                                    .font(.system(size: 10))
                            }
                            .buttonStyle(.plain)
                            .help("Open in Browser")
                        }
                    }
                }
                .padding(.horizontal, 4)
            }
            .onHover { hovering in
                self.isHovering = hovering
            }

            if !isOutgoing { Spacer(minLength: 50) }
        }
    }

    private func copyText() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        if let text = message.textContent {
            pasteboard.setString(text, forType: .string)
        }
    }

    private func formatTime(_ timestampMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestampMs / 1000))
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
