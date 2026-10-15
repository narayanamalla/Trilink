package com.trilink.app.domain

import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.TransferStatus

sealed class TransferEvent {
    object StartTransfer : TransferEvent()
    object PeerOffline : TransferEvent()
    object PeerOnline : TransferEvent()
    object PayloadAvailable : TransferEvent()
    object StartDownload : TransferEvent()
    object CompleteDelivery : TransferEvent()
    data class Fail(val reason: String) : TransferEvent()
    object Cancel : TransferEvent()
    object Expire : TransferEvent()
}

class IllegalStateTransitionException(from: TransferStatus, event: TransferEvent) :
    IllegalStateException("Cannot transition from $from on event ${event::class.simpleName}")

object TransferStateMachine {

    fun nextState(
        current: TransferStatus,
        event: TransferEvent,
        mode: DeliveryMode = DeliveryMode.LOCAL
    ): TransferStatus {
        return when (event) {
            is TransferEvent.StartTransfer -> {
                when (current) {
                    TransferStatus.CREATED, TransferStatus.QUEUED, TransferStatus.FAILED -> TransferStatus.UPLOADING
                    TransferStatus.UPLOADING -> current // idempotent
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.PeerOffline -> {
                when (current) {
                    TransferStatus.CREATED -> TransferStatus.QUEUED
                    TransferStatus.QUEUED -> current
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.PeerOnline -> {
                when (current) {
                    TransferStatus.QUEUED -> TransferStatus.UPLOADING
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.PayloadAvailable -> {
                when (current) {
                    TransferStatus.UPLOADING -> TransferStatus.AVAILABLE
                    TransferStatus.AVAILABLE -> current
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.StartDownload -> {
                when (current) {
                    TransferStatus.AVAILABLE -> TransferStatus.DOWNLOADING
                    TransferStatus.DOWNLOADING -> current
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.CompleteDelivery -> {
                when (current) {
                    // For local transfers, uploading completes straight to delivered once stream finishes & integrity passes
                    TransferStatus.UPLOADING -> {
                        if (mode == DeliveryMode.LOCAL) TransferStatus.DELIVERED
                        else throw IllegalStateTransitionException(current, event)
                    }
                    TransferStatus.DOWNLOADING -> TransferStatus.DELIVERED
                    TransferStatus.DELIVERED -> current // idempotent delivery
                    else -> throw IllegalStateTransitionException(current, event)
                }
            }

            is TransferEvent.Fail -> {
                when (current) {
                    TransferStatus.DELIVERED, TransferStatus.CANCELLED, TransferStatus.EXPIRED -> current
                    else -> TransferStatus.FAILED
                }
            }

            is TransferEvent.Cancel -> {
                when (current) {
                    TransferStatus.DELIVERED, TransferStatus.EXPIRED -> current
                    else -> TransferStatus.CANCELLED
                }
            }

            is TransferEvent.Expire -> {
                when (current) {
                    TransferStatus.DELIVERED -> current
                    else -> TransferStatus.EXPIRED
                }
            }
        }
    }

    fun canTransition(current: TransferStatus, to: TransferStatus, mode: DeliveryMode = DeliveryMode.LOCAL): Boolean {
        if (current == to) return true
        return try {
            val event = when (to) {
                TransferStatus.QUEUED -> TransferEvent.PeerOffline
                TransferStatus.UPLOADING -> TransferEvent.StartTransfer
                TransferStatus.AVAILABLE -> TransferEvent.PayloadAvailable
                TransferStatus.DOWNLOADING -> TransferEvent.StartDownload
                TransferStatus.DELIVERED -> TransferEvent.CompleteDelivery
                TransferStatus.FAILED -> TransferEvent.Fail("Failed")
                TransferStatus.CANCELLED -> TransferEvent.Cancel
                TransferStatus.EXPIRED -> TransferEvent.Expire
                TransferStatus.CREATED -> null
            }
            if (event == null) false
            else nextState(current, event, mode) == to
        } catch (_: Exception) {
            false
        }
    }
}
