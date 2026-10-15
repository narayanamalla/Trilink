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
import java.util.UUID

class TriLinkApplication : Application() {

    lateinit var database: TriLinkDatabase
        private set

    lateinit var deviceRepository: DeviceRepository
        private set

    lateinit var transferRepository: TransferRepository
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

        currentDevicePayload = PairingPayload(
            version = 1,
            pairingId = UUID.randomUUID().toString(),
            deviceId = devId,
            deviceName = devName.ifBlank { "Android Device" },
            platform = "android",
            publicKey = "MCowBQYDK2VwAyEAXyZk0f8T1Gq2D9J1s5H2a4K7V8x3N6m0P1Q=",
            exchangeKey = "MCowBQYDK2VuAyEA9kL1m3N5p7Q9r1T3v5X7z9B1d3F5h7J9l=",
            localEndpoint = null
        )

        database = TriLinkDatabase.getInstance(this)
        deviceRepository = RealDeviceRepository(database.deviceDao()) { currentDevicePayload }
        transferRepository = RealTransferRepository(database.transferDao(), database.inboxDao())
    }
}
