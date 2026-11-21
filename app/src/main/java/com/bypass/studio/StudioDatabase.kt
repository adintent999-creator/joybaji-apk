package com.bypass.studio

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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memory")
data class MemoryEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT value FROM memory WHERE key = :key")
    suspend fun memory(key: String): String?

    @Upsert
    suspend fun putMemory(entity: MemoryEntity)

    @Query("SELECT * FROM memory")
    fun allMemory(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(entity: ChatEntity)

    @Query("SELECT * FROM chat WHERE session = :session ORDER BY id ASC")
    fun chat(session: String): Flow<List<ChatEntity>>

    @Query("DELETE FROM chat WHERE session = :session")
    suspend fun clearChat(session: String)

    @Query("SELECT DISTINCT session FROM chat ORDER BY id DESC")
    fun sessions(): Flow<List<String>>
}

@Database(entities = [MemoryEntity::class, ChatEntity::class], version = 1, exportSchema = false)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun dao(): StudioDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun get(context: Context): StudioDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, StudioDatabase::class.java, "bypass_studio.db")
                .build().also { INSTANCE = it }
        }
    }
}
