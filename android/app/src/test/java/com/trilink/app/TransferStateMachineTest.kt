package com.trilink.app

import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.TransferStatus
import com.trilink.app.domain.IllegalStateTransitionException
import com.trilink.app.domain.TransferEvent
import com.trilink.app.domain.TransferStateMachine
import org.junit.Assert.*
import org.junit.Test

class TransferStateMachineTest {

    @Test
    fun testLocalTransferHappyPath() {
        var state = TransferStatus.CREATED
        assertTrue(TransferStateMachine.canTransition(state, TransferStatus.UPLOADING, DeliveryMode.LOCAL))

        state = TransferStateMachine.nextState(state, TransferEvent.StartTransfer, DeliveryMode.LOCAL)
        assertEquals(TransferStatus.UPLOADING, state)

        state = TransferStateMachine.nextState(state, TransferEvent.CompleteDelivery, DeliveryMode.LOCAL)
        assertEquals(TransferStatus.DELIVERED, state)
    }

    @Test
    fun testRemoteOfflineTransferQueueAndResume() {
        var state = TransferStatus.CREATED

        // Recipient offline -> queue
        state = TransferStateMachine.nextState(state, TransferEvent.PeerOffline, DeliveryMode.REMOTE)
        assertEquals(TransferStatus.QUEUED, state)

        // Recipient online -> start upload
        state = TransferStateMachine.nextState(state, TransferEvent.PeerOnline, DeliveryMode.REMOTE)
        assertEquals(TransferStatus.UPLOADING, state)

        // Upload to cloud finished -> available
        state = TransferStateMachine.nextState(state, TransferEvent.PayloadAvailable, DeliveryMode.REMOTE)
        assertEquals(TransferStatus.AVAILABLE, state)

        // Recipient downloading
        state = TransferStateMachine.nextState(state, TransferEvent.StartDownload, DeliveryMode.REMOTE)
        assertEquals(TransferStatus.DOWNLOADING, state)

        // Recipient finished & verified -> delivered
        state = TransferStateMachine.nextState(state, TransferEvent.CompleteDelivery, DeliveryMode.REMOTE)
        assertEquals(TransferStatus.DELIVERED, state)
    }

    @Test
    fun testCancellationAndIdempotency() {
        var state = TransferStatus.UPLOADING
        state = TransferStateMachine.nextState(state, TransferEvent.Cancel)
        assertEquals(TransferStatus.CANCELLED, state)

        // Cancelling already cancelled or delivered state should be idempotent
        val afterSecondCancel = TransferStateMachine.nextState(state, TransferEvent.Cancel)
        assertEquals(TransferStatus.CANCELLED, afterSecondCancel)

        val delivered = TransferStatus.DELIVERED
        val cancelDelivered = TransferStateMachine.nextState(delivered, TransferEvent.Cancel)
        assertEquals(TransferStatus.DELIVERED, cancelDelivered)
    }

    @Test
    fun testFailureAndRetry() {
        var state = TransferStatus.UPLOADING
        state = TransferStateMachine.nextState(state, TransferEvent.Fail("Network timeout"))
        assertEquals(TransferStatus.FAILED, state)

        // Retry should transition back to UPLOADING
        state = TransferStateMachine.nextState(state, TransferEvent.StartTransfer)
        assertEquals(TransferStatus.UPLOADING, state)
    }

    @Test(expected = IllegalStateTransitionException::class)
    fun testInvalidTransitionThrows() {
        // Cannot complete delivery directly from CREATED
        TransferStateMachine.nextState(TransferStatus.CREATED, TransferEvent.CompleteDelivery, DeliveryMode.LOCAL)
    }
}
