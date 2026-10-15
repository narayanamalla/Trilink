package com.trilink.app

import android.app.Application
import com.trilink.app.data.database.TriLinkDatabase
import com.trilink.app.data.repository.DeviceRepository
import com.trilink.app.data.repository.RealDeviceRepository
import com.trilink.app.data.repository.RealTransferRepository
import com.trilink.app.data.repository.TransferRepository

class TriLinkApplication : Application() {

    lateinit var database: TriLinkDatabase
        private set

    lateinit var deviceRepository: DeviceRepository
        private set

    lateinit var transferRepository: TransferRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = TriLinkDatabase.getInstance(this)
        deviceRepository = RealDeviceRepository(database.deviceDao())
        transferRepository = RealTransferRepository(database.transferDao(), database.inboxDao())
    }
}
