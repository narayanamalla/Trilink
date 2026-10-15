package com.trilink.app.data.model

import java.util.UUID

enum class ItemType {
    TEXT,
    URL,
    IMAGE,
    FILE
}

enum class DeliveryMode {
    LOCAL,
    REMOTE
}

enum class TransferStatus {
    CREATED,
    QUEUED,
    UPLOADING,
    AVAILABLE,
    DOWNLOADING,
    DELIVERED,
    FAILED,
    CANCELLED,
    EXPIRED
}

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    val deviceName: String,
    val platform: String, // "android" or "macos"
    val publicKey: String,
    val exchangeKey: String,
    val localEndpoint: String? = null,
    val isOnlineLocally: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false
)

data class Transfer(
    val id: String = UUID.randomUUID().toString(),
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val itemType: ItemType,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
    val sha256: String,
    val deliveryMode: DeliveryMode,
    val status: TransferStatus = TransferStatus.CREATED,
    val bytesTransferred: Long = 0,
    val localPath: String? = null,
    val textContent: String? = null,
    val failureCode: String? = null,
    val failureReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PairingPayload(
    val version: Int = 1,
    val pairingId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val publicKey: String,
    val exchangeKey: String,
    val localEndpoint: String? = null,
    val expiresAt: Long = System.currentTimeMillis() + (180 * 1000) // 3 minutes
)
