import SwiftUI
import TriLinkCore

struct TransfersView: View {
    @EnvironmentObject var state: AppStateManager

    var body: some View {
        VStack(spacing: 0) {
            // Header bar
            HStack {
                Text("Files & Media")
                    .font(.title2)
                    .fontWeight(.bold)

                Spacer()

                Button("Open Downloads Folder") {
                    state.openDownloadsFolder()
                }
                .buttonStyle(.bordered)
            }
            .padding()

            Divider()

            if state.fileTransfers.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                    Image(systemName: "folder.badge.gearshape")
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                    Text("No files or photos yet")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    Text("Photos, videos, and documents transferred between your Mac and devices will appear here.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(state.fileTransfers) { transfer in
                        TransferRow(transfer: transfer) {
                            if let path = transfer.localPath {
                                state.openFileInFinder(path: path)
                            }
                        }
                    }
                }
                .listStyle(.inset(alternatesRowBackgrounds: true))
            }
        }
    }
}

struct TransferRow: View {
    let transfer: Transfer
    let onReveal: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: iconForType(transfer.itemType))
                .font(.title)
                .foregroundColor(.accentColor)
                .frame(width: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text(transfer.displayName)
                    .font(.headline)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    if transfer.sizeBytes > 0 {
                        Text(formatBytes(transfer.sizeBytes))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    if let text = transfer.textContent {
                        Text("\"\(text.prefix(40))\"")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Text("•")
                        .foregroundColor(.secondary)

                    StatusBadge(status: transfer.status)
                }

                if transfer.status == .uploading || transfer.status == .downloading {
                    let progress = transfer.sizeBytes > 0 ? Double(transfer.bytesTransferred) / Double(transfer.sizeBytes) : 0.5
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                        .frame(maxWidth: 200)
                }
            }

            Spacer()

            if transfer.status == .delivered && transfer.localPath != nil {
                Button("Show in Finder") {
                    onReveal()
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }
        .padding(.vertical, 6)
    }

    private func iconForType(_ type: ItemType) -> String {
        switch type {
        case .image: return "photo.fill"
        case .file: return "doc.fill"
        case .text: return "text.alignleft"
        case .url: return "link"
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useAll]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

struct StatusBadge: View {
    let status: TransferStatus

    var body: some View {
        Text(status.rawValue.capitalized)
            .font(.caption2)
            .fontWeight(.bold)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(backgroundColor.opacity(0.15))
            .foregroundColor(backgroundColor)
            .cornerRadius(4)
    }

    private var backgroundColor: Color {
        switch status {
        case .delivered: return .green
        case .uploading, .downloading: return .blue
        case .failed: return .red
        case .cancelled, .expired: return .secondary
        default: return .orange
        }
    }
}
