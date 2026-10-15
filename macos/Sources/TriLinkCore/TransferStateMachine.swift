import Foundation

public enum TransferEvent: Sendable {
    case startTransfer
    case peerOffline
    case peerOnline
    case payloadAvailable
    case startDownload
    case completeDelivery
    case fail(reason: String)
    case cancel
    case expire
}

public struct StateMachineError: Error, CustomStringConvertible {
    public let message: String
    public var description: String { message }
}

public enum TransferStateMachine {
    public static func nextState(
        current: TransferStatus,
        event: TransferEvent,
        mode: DeliveryMode = .local
    ) throws -> TransferStatus {
        switch event {
        case .startTransfer:
            switch current {
            case .created, .queued, .failed:
                return .uploading
            case .uploading:
                return current // idempotent
            default:
                throw StateMachineError(message: "Cannot start transfer from \(current)")
            }

        case .peerOffline:
            switch current {
            case .created:
                return .queued
            case .queued:
                return current
            default:
                throw StateMachineError(message: "Cannot transition to queued from \(current)")
            }

        case .peerOnline:
            switch current {
            case .queued:
                return .uploading
            default:
                throw StateMachineError(message: "Cannot transition to uploading from \(current)")
            }

        case .payloadAvailable:
            switch current {
            case .uploading:
                return .available
            case .available:
                return current
            default:
                throw StateMachineError(message: "Cannot mark available from \(current)")
            }

        case .startDownload:
            switch current {
            case .available:
                return .downloading
            case .downloading:
                return current
            default:
                throw StateMachineError(message: "Cannot start downloading from \(current)")
            }

        case .completeDelivery:
            switch current {
            case .uploading:
                if mode == .local {
                    return .delivered
                } else {
                    throw StateMachineError(message: "Remote uploading must transition through available and downloading first")
                }
            case .downloading:
                return .delivered
            case .delivered:
                return current // idempotent
            default:
                throw StateMachineError(message: "Cannot complete delivery from \(current)")
            }

        case .fail:
            switch current {
            case .delivered, .cancelled, .expired:
                return current
            default:
                return .failed
            }

        case .cancel:
            switch current {
            case .delivered, .expired:
                return current
            default:
                return .cancelled
            }

        case .expire:
            switch current {
            case .delivered:
                return current
            default:
                return .expired
            }
        }
    }

    public static func canTransition(
        current: TransferStatus,
        to target: TransferStatus,
        mode: DeliveryMode = .local
    ) -> Bool {
        if current == target { return true }
        let event: TransferEvent? = {
            switch target {
            case .queued: return .peerOffline
            case .uploading: return .startTransfer
            case .available: return .payloadAvailable
            case .downloading: return .startDownload
            case .delivered: return .completeDelivery
            case .failed: return .fail(reason: "Failed")
            case .cancelled: return .cancel
            case .expired: return .expire
            case .created: return nil
            }
        }()

        guard let ev = event else { return false }
        do {
            let next = try nextState(current: current, event: ev, mode: mode)
            return next == target
        } catch {
            return false
        }
    }
}
