#!/usr/bin/env python3
"""
TriLink Transfer State Machine Test Suite.
Validates state transitions, offline queuing, cancellation, idempotency, and retries.
"""
import sys

STATES = {
    "CREATED", "QUEUED", "UPLOADING", "AVAILABLE",
    "DOWNLOADING", "DELIVERED", "FAILED", "CANCELLED", "EXPIRED"
}

class IllegalStateTransition(Exception):
    pass

class TransferStateMachine:
    @staticmethod
    def next_state(current: str, event: str, mode: str = "LOCAL") -> str:
        if event == "START_TRANSFER":
            if current in ("CREATED", "QUEUED", "FAILED"):
                return "UPLOADING"
            elif current == "UPLOADING":
                return current # idempotent
            raise IllegalStateTransition(f"Cannot start transfer from {current}")

        elif event == "PEER_OFFLINE":
            if current == "CREATED":
                return "QUEUED"
            elif current == "QUEUED":
                return current
            raise IllegalStateTransition(f"Cannot transition to queued from {current}")

        elif event == "PEER_ONLINE":
            if current == "QUEUED":
                return "UPLOADING"
            raise IllegalStateTransition(f"Cannot transition to uploading from {current}")

        elif event == "PAYLOAD_AVAILABLE":
            if current == "UPLOADING":
                return "AVAILABLE"
            elif current == "AVAILABLE":
                return current
            raise IllegalStateTransition(f"Cannot mark available from {current}")

        elif event == "START_DOWNLOAD":
            if current == "AVAILABLE":
                return "DOWNLOADING"
            elif current == "DOWNLOADING":
                return current
            raise IllegalStateTransition(f"Cannot start download from {current}")

        elif event == "COMPLETE_DELIVERY":
            if current == "UPLOADING":
                if mode == "LOCAL":
                    return "DELIVERED"
                raise IllegalStateTransition("Remote upload must transition through AVAILABLE and DOWNLOADING")
            elif current == "DOWNLOADING":
                return "DELIVERED"
            elif current == "DELIVERED":
                return current # idempotent delivery
            raise IllegalStateTransition(f"Cannot complete delivery from {current}")

        elif event == "FAIL":
            if current in ("DELIVERED", "CANCELLED", "EXPIRED"):
                return current
            return "FAILED"

        elif event == "CANCEL":
            if current in ("DELIVERED", "EXPIRED"):
                return current
            return "CANCELLED"

        elif event == "EXPIRE":
            if current == "DELIVERED":
                return current
            return "EXPIRED"

        raise ValueError(f"Unknown event: {event}")

def test_local_happy_path():
    state = "CREATED"
    state = TransferStateMachine.next_state(state, "START_TRANSFER", "LOCAL")
    assert state == "UPLOADING"
    state = TransferStateMachine.next_state(state, "COMPLETE_DELIVERY", "LOCAL")
    assert state == "DELIVERED"
    # Idempotent re-delivery
    assert TransferStateMachine.next_state(state, "COMPLETE_DELIVERY", "LOCAL") == "DELIVERED"
    print("✓ Local happy path and idempotency verified")

def test_remote_offline_flow():
    state = "CREATED"
    state = TransferStateMachine.next_state(state, "PEER_OFFLINE", "REMOTE")
    assert state == "QUEUED"
    state = TransferStateMachine.next_state(state, "PEER_ONLINE", "REMOTE")
    assert state == "UPLOADING"
    state = TransferStateMachine.next_state(state, "PAYLOAD_AVAILABLE", "REMOTE")
    assert state == "AVAILABLE"
    state = TransferStateMachine.next_state(state, "START_DOWNLOAD", "REMOTE")
    assert state == "DOWNLOADING"
    state = TransferStateMachine.next_state(state, "COMPLETE_DELIVERY", "REMOTE")
    assert state == "DELIVERED"
    print("✓ Remote offline queue and multi-step delivery verified")

def test_cancellation_and_failure():
    state = "UPLOADING"
    cancelled = TransferStateMachine.next_state(state, "CANCEL")
    assert cancelled == "CANCELLED"

    failed = TransferStateMachine.next_state(state, "FAIL")
    assert failed == "FAILED"
    # Retry from failed
    retried = TransferStateMachine.next_state(failed, "START_TRANSFER")
    assert retried == "UPLOADING"
    print("✓ Cancellation, failure, and retry paths verified")

def test_invalid_transitions():
    try:
        TransferStateMachine.next_state("CREATED", "COMPLETE_DELIVERY", "LOCAL")
        assert False, "Should have thrown IllegalStateTransition"
    except IllegalStateTransition:
        pass
    print("✓ Illegal transition protection verified")

if __name__ == "__main__":
    print("Running TriLink State Machine Verification Suite...")
    test_local_happy_path()
    test_remote_offline_flow()
    test_cancellation_and_failure()
    test_invalid_transitions()
    print("All State Machine Tests PASSED successfully.")
