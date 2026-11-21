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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory")
data class Memory(
    @PrimaryKey val key: String,
    val value: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM chats WHERE sessionId = :sid ORDER BY id ASC")
    fun chat(sid: String): Flow<List<Chat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Query("DELETE FROM chats WHERE sessionId = :sid")
    suspend fun clearChat(sid: String)

    @Query("SELECT sessionId FROM chats GROUP BY sessionId ORDER BY MAX(id) DESC")
    fun sessions(): Flow<List<String>>

    @Query("SELECT * FROM memory ORDER BY ts DESC")
    fun allMemory(): Flow<List<Memory>>

    @Query("SELECT * FROM memory WHERE key LIKE '%' || :q || '%' OR value LIKE '%' || :q || '%' ORDER BY ts DESC")
    suspend fun searchMemory(q: String): List<Memory>

    @Query("DELETE FROM memory WHERE key = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM memory")
    suspend fun clearMemory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMemory(mem: Memory)
}

@Database(entities = [Chat::class, Memory::class], version = 2, exportSchema = false)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun dao(): StudioDao

    companion object {
        @Volatile private var INSTANCE: StudioDatabase? = null

        fun get(context: Context): StudioDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                StudioDatabase::class.java,
                "bypass_studio.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory ADD COLUMN ts INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
