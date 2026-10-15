package com.trilink.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.trilink.app.data.database.TriLinkDatabase
import com.trilink.app.data.model.PairingPayload
import com.trilink.app.data.repository.DeviceRepository
import com.trilink.app.data.repository.RealDeviceRepository
import com.trilink.app.data.repository.RealTransferRepository
import com.trilink.app.data.repository.TransferRepository
import com.trilink.app.data.repository.LocalTransferServer
import com.trilink.app.domain.NetworkUtils
import java.util.UUID

class TriLinkApplication : Application() {

    lateinit var database: TriLinkDatabase
        private set

    lateinit var deviceRepository: DeviceRepository
        private set

    lateinit var transferRepository: TransferRepository
        private set

    lateinit var localTransferServer: LocalTransferServer
        private set

    lateinit var currentDevicePayload: PairingPayload
        private set

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("trilink_prefs", Context.MODE_PRIVATE)
        var devId = prefs.getString("device_id", null)
        if (devId == null) {
            devId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", devId).apply()
        }

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val devName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

        val localIp = NetworkUtils.getLocalIpAddress()
        val localEndpoint = if (!localIp.isNullOrBlank()) "http://$localIp:42426" else null

        currentDevicePayload = PairingPayload(
            version = 1,
            pairingId = UUID.randomUUID().toString(),
            deviceId = devId,
            deviceName = devName.ifBlank { "Android Device" },
            platform = "android",
            publicKey = "MCowBQYDK2VwAyEAXyZk0f8T1Gq2D9J1s5H2a4K7V8x3N6m0P1Q=",
            exchangeKey = "MCowBQYDK2VuAyEA9kL1m3N5p7Q9r1T3v5X7z9B1d3F5h7J9l=",
            localEndpoint = localEndpoint
        )

        database = TriLinkDatabase.getInstance(this)
        deviceRepository = RealDeviceRepository(database.deviceDao()) {
            val ip = NetworkUtils.getLocalIpAddress()
            val ep = if (!ip.isNullOrBlank()) "http://$ip:42426" else currentDevicePayload.localEndpoint
            currentDevicePayload.copy(localEndpoint = ep)
        }
        transferRepository = RealTransferRepository(database.transferDao(), database.inboxDao())

        // Start background local transfer server to receive incoming files/notes from Mac
        localTransferServer = LocalTransferServer(
            context = this,
            port = 42426,
            transferRepo = transferRepository,
            deviceRepo = deviceRepository
        )
        localTransferServer.start()
    }
}
