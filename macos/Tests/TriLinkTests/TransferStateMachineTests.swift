import XCTest
@testable import TriLinkCore

final class TransferStateMachineTests: XCTestCase {

    func testLocalTransferFlow() throws {
        var state: TransferStatus = .created
        XCTAssertTrue(TransferStateMachine.canTransition(current: state, to: .uploading, mode: .local))

        state = try TransferStateMachine.nextState(current: state, event: .startTransfer, mode: .local)
        XCTAssertEqual(state, .uploading)

        state = try TransferStateMachine.nextState(current: state, event: .completeDelivery, mode: .local)
        XCTAssertEqual(state, .delivered)
    }

    func testRemoteOfflineTransferFlow() throws {
        var state: TransferStatus = .created

        // Recipient offline -> queue
        state = try TransferStateMachine.nextState(current: state, event: .peerOffline, mode: .remote)
        XCTAssertEqual(state, .queued)

        // Recipient comes online -> start upload to cloud
        state = try TransferStateMachine.nextState(current: state, event: .peerOnline, mode: .remote)
        XCTAssertEqual(state, .uploading)

        // Upload finished -> available in cloud
        state = try TransferStateMachine.nextState(current: state, event: .payloadAvailable, mode: .remote)
        XCTAssertEqual(state, .available)

        // Recipient begins download
        state = try TransferStateMachine.nextState(current: state, event: .startDownload, mode: .remote)
        XCTAssertEqual(state, .downloading)

        // Download finished and verified -> delivered
        state = try TransferStateMachine.nextState(current: state, event: .completeDelivery, mode: .remote)
        XCTAssertEqual(state, .delivered)
    }

    func testCancellationAndIdempotency() throws {
        var state: TransferStatus = .uploading
        state = try TransferStateMachine.nextState(current: state, event: .cancel)
        XCTAssertEqual(state, .cancelled)

        // Repeated cancellation is idempotent
        let secondCancel = try TransferStateMachine.nextState(current: state, event: .cancel)
        XCTAssertEqual(secondCancel, .cancelled)

        // Delivered items cannot be cancelled
        let delivered: TransferStatus = .delivered
        let cancelDelivered = try TransferStateMachine.nextState(current: delivered, event: .cancel)
        XCTAssertEqual(cancelDelivered, .delivered)
    }

    func testFailureAndRetry() throws {
        var state: TransferStatus = .uploading
        state = try TransferStateMachine.nextState(current: state, event: .fail(reason: "Network timeout"))
        XCTAssertEqual(state, .failed)

        // Retry transitions back to uploading
        state = try TransferStateMachine.nextState(current: state, event: .startTransfer)
        XCTAssertEqual(state, .uploading)
    }

    func testInvalidStateTransitionThrows() {
        XCTAssertThrowsError(try TransferStateMachine.nextState(current: .created, event: .completeDelivery, mode: .local))
    }
}
