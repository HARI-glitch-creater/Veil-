package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class VeilTypeConverters {
    @TypeConverter
    fun fromBackendType(value: BackendType): String = value.name

    @TypeConverter
    fun toBackendType(value: String): BackendType =
        runCatching { BackendType.valueOf(value) }.getOrDefault(BackendType.SUPABASE)

    @TypeConverter
    fun fromAuthType(value: AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): AuthType =
        runCatching { AuthType.valueOf(value) }.getOrDefault(AuthType.BEARER)

    @TypeConverter
    fun fromRoomRole(value: RoomRole): String = value.name

    @TypeConverter
    fun toRoomRole(value: String): RoomRole =
        runCatching { RoomRole.valueOf(value) }.getOrDefault(RoomRole.HOST)

    @TypeConverter
    fun fromContentType(value: MessageContentType): String = value.name

    @TypeConverter
    fun toContentType(value: String): MessageContentType =
        runCatching { MessageContentType.valueOf(value) }.getOrDefault(MessageContentType.TEXT)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.PENDING)
}

@Database(
    entities = [
        BackendProfileEntity::class,
        VeilRoomEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(VeilTypeConverters::class)
abstract class VeilDatabase : RoomDatabase() {
    abstract fun veilDao(): VeilDao

    companion object {
        @Volatile
        private var INSTANCE: VeilDatabase? = null

        fun getInstance(context: Context): VeilDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VeilDatabase::class.java,
                    "veil_local_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
