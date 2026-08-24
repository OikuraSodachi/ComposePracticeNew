package com.todokanai.composepracticenew.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.todokanai.composepracticenew.myobjects.Constants

/** 원격 스토리지 접속 정보를 저장하는 엔티티. */
@Entity(tableName = "remote_storage_info")
data class RemoteStorageInfo(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo val id: Long = 0,
    @ColumnInfo val name: String,
    @ColumnInfo val address: String,
    @ColumnInfo val port: Int,
    @ColumnInfo val userId: String,
    @ColumnInfo val password: String,
    @ColumnInfo val encoding: String = Constants.FTP_ENCODING_DEFAULT
)
