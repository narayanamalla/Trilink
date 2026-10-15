package com.trilink.app.data.repository

import com.trilink.app.data.database.InboxDao
import com.trilink.app.data.database.InboxEntity
import com.trilink.app.data.database.TransferDao
import com.trilink.app.data.database.TransferEntity
import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.model.TransferStatus
import com.trilink.app.domain.TransferEvent
import com.trilink.app.domain.TransferStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class InboxItem(
    val id: String = UUID.randomUUID().toString(),
    val transferId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val itemType: ItemType,
    val displayName: String,
    val sizeBytes: Long,
    val localPath: String? = null,
    val textContent: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

interface TransferRepository {
    fun getAllTransfers(): Flow<List<Transfer>>
    fun getActiveTransfers(): Flow<List<Transfer>>
    fun getInboxItems(): Flow<List<InboxItem>>
    suspend fun getTransfer(id: String): Transfer?
    suspend fun createTransfer(transfer: Transfer)
    suspend fun updateTransferProgress(id: String, status: TransferStatus, bytesTransferred: Long)
    suspend fun markFailed(id: String, code: String?, reason: String?)
    suspend fun cancelTransfer(id: String)
    suspend fun recordInboxItem(item: InboxItem)
    suspend fun deleteInboxItem(id: String)
}

class RealTransferRepository(
    private val transferDao: TransferDao,
    private val inboxDao: InboxDao
) : TransferRepository {

    override fun getAllTransfers(): Flow<List<Transfer>> {
        return transferDao.getAllTransfers().map { list -> list.map { it.toModel() } }
    }

    override fun getActiveTransfers(): Flow<List<Transfer>> {
        return transferDao.getActiveTransfers().map { list -> list.map { it.toModel() } }
    }

    override fun getInboxItems(): Flow<List<InboxItem>> {
        return inboxDao.getAllInboxItems().map { list -> list.map { it.toModel() } }
    }

    override suspend fun getTransfer(id: String): Transfer? {
        return transferDao.getTransferById(id)?.toModel()
    }

    override suspend fun createTransfer(transfer: Transfer) {
        transferDao.insertOrUpdate(transfer.toEntity())
    }

    override suspend fun updateTransferProgress(id: String, status: TransferStatus, bytesTransferred: Long) {
        transferDao.updateProgress(id, status.name, bytesTransferred, System.currentTimeMillis())
    }

    override suspend fun markFailed(id: String, code: String?, reason: String?) {
        transferDao.markFailed(id, TransferStatus.FAILED.name, code, reason, System.currentTimeMillis())
    }

    override suspend fun cancelTransfer(id: String) {
        val current = transferDao.getTransferById(id)?.toModel() ?: return
        if (TransferStateMachine.canTransition(current.status, TransferStatus.CANCELLED, current.deliveryMode)) {
            transferDao.updateProgress(id, TransferStatus.CANCELLED.name, current.bytesTransferred, System.currentTimeMillis())
        }
    }

    override suspend fun recordInboxItem(item: InboxItem) {
        inboxDao.insertInboxItem(item.toEntity())
    }

    override suspend fun deleteInboxItem(id: String) {
        inboxDao.deleteInboxItem(id)
    }
}

/**
 * Isolated fake repository strictly for Compose UI previews, instrumented design testing,
 * and sandbox preview environments without database initialization.
 * Must NOT be used in production runtime.
 */
class FakeTransferRepository : TransferRepository {
    private val transfersFlow = MutableStateFlow<List<Transfer>>(emptyList())
    private val inboxFlow = MutableStateFlow<List<InboxItem>>(emptyList())

    override fun getAllTransfers(): Flow<List<Transfer>> = transfersFlow
    override fun getActiveTransfers(): Flow<List<Transfer>> = transfersFlow.map { list ->
        list.filter { it.status == TransferStatus.UPLOADING || it.status == TransferStatus.DOWNLOADING || it.status == TransferStatus.QUEUED }
    }
    override fun getInboxItems(): Flow<List<InboxItem>> = inboxFlow
    override suspend fun getTransfer(id: String): Transfer? = transfersFlow.value.find { it.id == id }

    override suspend fun createTransfer(transfer: Transfer) {
        transfersFlow.value = listOf(transfer) + transfersFlow.value
    }

    override suspend fun updateTransferProgress(id: String, status: TransferStatus, bytesTransferred: Long) {
        transfersFlow.value = transfersFlow.value.map {
            if (it.id == id) it.copy(status = status, bytesTransferred = bytesTransferred, updatedAt = System.currentTimeMillis())
            else it
        }
    }

    override suspend fun markFailed(id: String, code: String?, reason: String?) {
        transfersFlow.value = transfersFlow.value.map {
            if (it.id == id) it.copy(status = TransferStatus.FAILED, failureCode = code, failureReason = reason, updatedAt = System.currentTimeMillis())
            else it
        }
    }

    override suspend fun cancelTransfer(id: String) {
        transfersFlow.value = transfersFlow.value.map {
            if (it.id == id) it.copy(status = TransferStatus.CANCELLED, updatedAt = System.currentTimeMillis())
            else it
        }
    }

    override suspend fun recordInboxItem(item: InboxItem) {
        inboxFlow.value = listOf(item) + inboxFlow.value
    }

    override suspend fun deleteInboxItem(id: String) {
        inboxFlow.value = inboxFlow.value.filter { it.id != id }
    }
}

fun TransferEntity.toModel(): Transfer = Transfer(
    id = id,
    senderDeviceId = senderDeviceId,
    recipientDeviceId = recipientDeviceId,
    itemType = ItemType.valueOf(itemType),
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    deliveryMode = DeliveryMode.valueOf(deliveryMode),
    status = TransferStatus.valueOf(status),
    bytesTransferred = bytesTransferred,
    localPath = localPath,
    textContent = textContent,
    failureCode = failureCode,
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transfer.toEntity(): TransferEntity = TransferEntity(
    id = id,
    senderDeviceId = senderDeviceId,
    recipientDeviceId = recipientDeviceId,
    itemType = itemType.name,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    deliveryMode = deliveryMode.name,
    status = status.name,
    bytesTransferred = bytesTransferred,
    localPath = localPath,
    textContent = textContent,
    failureCode = failureCode,
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun InboxEntity.toModel(): InboxItem = InboxItem(
    id = id,
    transferId = transferId,
    senderDeviceId = senderDeviceId,
    senderDeviceName = senderDeviceName,
    itemType = ItemType.valueOf(itemType),
    displayName = displayName,
    sizeBytes = sizeBytes,
    localPath = localPath,
    textContent = textContent,
    receivedAt = receivedAt
)

fun InboxItem.toEntity(): InboxEntity = InboxEntity(
    id = id,
    transferId = transferId,
    senderDeviceId = senderDeviceId,
    senderDeviceName = senderDeviceName,
    itemType = itemType.name,
    displayName = displayName,
    sizeBytes = sizeBytes,
    localPath = localPath,
    textContent = textContent,
    receivedAt = receivedAt
)
