package com.trilink.app.data.repository

import com.trilink.app.data.database.DeviceDao
import com.trilink.app.data.database.DeviceEntity
import com.trilink.app.data.model.Device
import com.trilink.app.data.model.PairingPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DeviceRepository {
    fun getTrustedDevices(): Flow<List<Device>>
    suspend fun getDevice(id: String): Device?
    suspend fun pairDevice(payload: PairingPayload): Result<Device>
    suspend fun updateReachability(id: String, isOnline: Boolean, endpoint: String?)
    suspend fun revokeDevice(id: String)
    suspend fun deleteDevice(id: String)
}

class RealDeviceRepository(
    private val deviceDao: DeviceDao
) : DeviceRepository {

    override fun getTrustedDevices(): Flow<List<Device>> {
        return deviceDao.getAllTrustedDevices().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getDevice(id: String): Device? {
        return deviceDao.getDeviceById(id)?.toModel()
    }

    override suspend fun pairDevice(payload: PairingPayload): Result<Device> {
        if (System.currentTimeMillis() > payload.expiresAt) {
            return Result.failure(IllegalArgumentException("Pairing QR has expired."))
        }
        val entity = DeviceEntity(
            id = payload.deviceId,
            ownerId = null,
            deviceName = payload.deviceName,
            platform = payload.platform,
            publicKey = payload.publicKey,
            exchangeKey = payload.exchangeKey,
            localEndpoint = payload.localEndpoint,
            isOnlineLocally = true,
            lastSeenAt = System.currentTimeMillis(),
            isRevoked = false
        )
        deviceDao.insertOrUpdate(entity)
        return Result.success(entity.toModel())
    }

    override suspend fun updateReachability(id: String, isOnline: Boolean, endpoint: String?) {
        deviceDao.updateReachability(id, isOnline, System.currentTimeMillis(), endpoint)
    }

    override suspend fun revokeDevice(id: String) {
        deviceDao.revokeDevice(id)
    }

    override suspend fun deleteDevice(id: String) {
        deviceDao.deleteDevice(id)
    }
}

fun DeviceEntity.toModel(): Device = Device(
    id = id,
    ownerId = ownerId,
    deviceName = deviceName,
    platform = platform,
    publicKey = publicKey,
    exchangeKey = exchangeKey,
    localEndpoint = localEndpoint,
    isOnlineLocally = isOnlineLocally,
    lastSeenAt = lastSeenAt,
    isRevoked = isRevoked
)
