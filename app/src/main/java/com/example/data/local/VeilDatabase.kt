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
    fun fromChannelType(value: ChannelType): String = value.name

    @TypeConverter
    fun toChannelType(value: String): ChannelType =
        runCatching { ChannelType.valueOf(value) }.getOrDefault(ChannelType.DIRECT)

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

    @TypeConverter
    fun fromCallDirection(value: CallDirection): String = value.name

    @TypeConverter
    fun toCallDirection(value: String): CallDirection =
        runCatching { CallDirection.valueOf(value) }.getOrDefault(CallDirection.OUTGOING)
}

@Database(
    entities = [
        UserProfileEntity::class,
        BackendProfileEntity::class,
        VeilRoomEntity::class,
        RoomMemberEntity::class,
        CommunityEntity::class,
        CommunityMemberEntity::class,
        MessageEntity::class,
        CallHistoryEntity::class
    ],
    version = 2,
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
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
