package com.krishna.webhub.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String = "",
    val icon: String? = null,
    val entry: String = "index.html",
    val orientation: String = "auto",
    val fullscreen: Boolean = false,
    val keepAwake: Boolean = false,
    val allowInternet: Boolean = false,
    val shakeAction: String = "default",
    val pinned: Boolean = false,
    val sizeBytes: Long = 0,
    val lastOpened: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AppDao {
    @Query("SELECT * FROM apps ORDER BY pinned DESC, lastOpened DESC, createdAt DESC")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps WHERE id = :id")
    fun observe(id: String): Flow<AppEntity?>

    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun get(id: String): AppEntity?

    @Query("SELECT * FROM apps")
    suspend fun all(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AppEntity)

    @Update
    suspend fun update(app: AppEntity)

    @Query("DELETE FROM apps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE apps SET lastOpened = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)
}

@Database(entities = [AppEntity::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
}
