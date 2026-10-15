package com.trilink.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class,
        TransferEntity::class,
        InboxEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TriLinkDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun transferDao(): TransferDao
    abstract fun inboxDao(): InboxDao

    companion object {
        @Volatile
        private var INSTANCE: TriLinkDatabase? = null

        fun getInstance(context: Context): TriLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TriLinkDatabase::class.java,
                    "trilink.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
