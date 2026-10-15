package com.trilink.app.data.repository

import com.trilink.app.data.database.DeviceDao
import com.trilink.app.data.database.DeviceEntity
import com.trilink.app.data.model.Device
import com.trilink.app.data.model.PairingPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

interface DeviceRepository {
    fun getTrustedDevices(): Flow<List<Device>>
    suspend fun getDevice(id: String): Device?
    suspend fun pairDevice(payload: PairingPayload): Result<Device>
    suspend fun updateReachability(id: String, isOnline: Boolean, endpoint: String?)
    suspend fun revokeDevice(id: String)
    suspend fun deleteDevice(id: String)
}

class RealDeviceRepository(
    private val deviceDao: DeviceDao,
    private val myDevicePayloadProvider: () -> PairingPayload = {
        PairingPayload(
            version = 1,
            pairingId = UUID.randomUUID().toString(),
            deviceId = "android-device-local",
            deviceName = "Android Device",
            platform = "android",
            publicKey = "MCowBQYDK2VwAyEAXyZ...",
            exchangeKey = "MCowBQYDK2VuAyEA9kL...",
            localEndpoint = null
        )
    }
) : DeviceRepository {

    override fun getTrustedDevices(): Flow<List<Device>> {
        return deviceDao.getAllTrustedDevices().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getDevice(id: String): Device? {
        return deviceDao.getDeviceById(id)?.toModel()
    }

    override suspend fun pairDevice(payload: PairingPayload): Result<Device> = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > payload.expiresAt) {
            return@withContext Result.failure(IllegalArgumentException("Pairing code has expired."))
        }

        val endpoint = payload.localEndpoint
        if (!endpoint.isNullOrBlank()) {
            try {
                val cleanEndpoint = endpoint.trim().removeSuffix("/")
                val url = URL("$cleanEndpoint/api/v1/pair")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true

                val myPayload = myDevicePayloadProvider()
                val json = JSONObject().apply {
                    put("version", 1)
                    put("pairing_id", payload.pairingId)
                    put("device_id", myPayload.deviceId)
                    put("device_name", myPayload.deviceName)
                    put("platform", "android")
                    put("public_key", myPayload.publicKey)
                    put("exchange_key", myPayload.exchangeKey)
                    put("local_endpoint", myPayload.localEndpoint ?: "")
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Peer rejected pairing (HTTP $responseCode)"))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Could not connect to ${payload.deviceName} at $endpoint: ${e.message}"))
            }
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
        Result.success(entity.toModel())
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
