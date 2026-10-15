package com.trilink.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.model.TransferStatus

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val ownerId: String?,
    val deviceName: String,
    val platform: String,
    val publicKey: String,
    val exchangeKey: String,
    val localEndpoint: String?,
    val isOnlineLocally: Boolean,
    val lastSeenAt: Long,
    val isRevoked: Boolean
)

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val itemType: String, // ItemType enum name
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
    val deliveryMode: String, // DeliveryMode enum name
    val status: String, // TransferStatus enum name
    val bytesTransferred: Long,
    val localPath: String?,
    val textContent: String?,
    val failureCode: String?,
    val failureReason: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "inbox")
data class InboxEntity(
    @PrimaryKey val id: String,
    val transferId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val itemType: String,
    val displayName: String,
    val sizeBytes: Long,
    val localPath: String?,
    val textContent: String?,
    val receivedAt: Long,
    val isRead: Boolean = false
)
