package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "encrypted_chat_vault")
data class VaultChatEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val senderDeviceId: String,
    val senderName: String,
    val senderRole: String,
    val messageType: String,
    val encryptedPayload: String,
    val encryptedMediaBase64: String,
    val durationSec: Long,
    val timestampMs: Long
)

@Entity(tableName = "encrypted_saved_media")
data class SavedMediaEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val mediaType: String, // "CAMERA_SNAPSHOT" or "VOICE_CLIP"
    val encryptedBase64: String,
    val encryptedCaption: String,
    val createdAtMs: Long
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM encrypted_chat_vault WHERE userId = :userId ORDER BY timestampMs ASC")
    fun observeEncryptedChats(userId: String): Flow<List<VaultChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncryptedChat(item: VaultChatEntity)

    @Query("SELECT * FROM encrypted_saved_media WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeSavedMedia(userId: String): Flow<List<SavedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedMedia(media: SavedMediaEntity)

    @Query("DELETE FROM encrypted_saved_media WHERE id = :id")
    suspend fun deleteSavedMedia(id: String)

    @Query("DELETE FROM encrypted_chat_vault WHERE userId = :userId")
    suspend fun deleteAllChats(userId: String)

    @Query("DELETE FROM encrypted_saved_media WHERE userId = :userId")
    suspend fun deleteAllMedia(userId: String)
}

@Database(
    entities = [VaultChatEntity::class, SavedMediaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: LocalVaultDatabase? = null

        fun getInstance(context: Context): LocalVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalVaultDatabase::class.java,
                    "whatsupp_encrypted_vault.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
