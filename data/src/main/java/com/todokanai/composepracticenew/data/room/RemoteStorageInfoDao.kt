package com.todokanai.composepracticenew.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** remote_storage_info 테이블에 대한 CRUD 접근자. */
@Dao
interface RemoteStorageInfoDao {

    @Query("SELECT * FROM remote_storage_info")
    fun getAll(): Flow<List<RemoteStorageInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(info: RemoteStorageInfo)

    @Delete
    suspend fun delete(info: RemoteStorageInfo)

    @Query("DELETE FROM remote_storage_info")
    suspend fun deleteAll()
}
