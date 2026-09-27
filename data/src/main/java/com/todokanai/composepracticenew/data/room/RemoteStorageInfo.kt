package com.todokanai.composepracticenew.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.todokanai.composepracticenew.myobjects.Constants

/**
 * 원격 스토리지 접속 정보를 저장하는 엔티티.
 * @param id 자동 생성 기본 키
 * @param name 스토리지 표시 이름
 * @param address 서버 주소
 * @param port 서버 포트 번호
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param encoding 파일명 인코딩
 */
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
