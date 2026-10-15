package com.trilink.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE isRevoked = 0 ORDER BY lastSeenAt DESC")
    fun getAllTrustedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Query("UPDATE devices SET isRevoked = 1 WHERE id = :id")
    suspend fun revokeDevice(id: String)

    @Query("UPDATE devices SET isOnlineLocally = :isOnline, lastSeenAt = :lastSeen, localEndpoint = :endpoint WHERE id = :id")
    suspend fun updateReachability(id: String, isOnline: Boolean, lastSeen: Long, endpoint: String?)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteDevice(id: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE status IN ('UPLOADING', 'DOWNLOADING', 'QUEUED') ORDER BY createdAt DESC")
    fun getActiveTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getTransferById(id: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(transfer: TransferEntity)

    @Query("UPDATE transfers SET status = :status, bytesTransferred = :bytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, bytes: Long, updatedAt: Long)

    @Query("UPDATE transfers SET status = :status, failureCode = :code, failureReason = :reason, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFailed(id: String, status: String, code: String?, reason: String?, updatedAt: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteTransfer(id: String)
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox ORDER BY receivedAt DESC")
    fun getAllInboxItems(): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox WHERE id = :id LIMIT 1")
    suspend fun getInboxItemById(id: String): InboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInboxItem(item: InboxEntity)

    @Query("DELETE FROM inbox WHERE id = :id")
    suspend fun deleteInboxItem(id: String)
}
