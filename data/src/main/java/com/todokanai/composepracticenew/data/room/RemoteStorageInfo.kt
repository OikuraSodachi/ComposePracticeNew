package com.todokanai.composepracticenew.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** [RemoteStorageInfo.protocol]에 저장하는 FTP 값. DB에 저장되는 값이므로 바꾸지 않는다. */
internal const val PROTOCOL_FTP = "FTP"

/**
 * 원격 스토리지 접속 정보를 저장하는 엔티티.
 * @param id 자동 생성 기본 키
 * @param name 스토리지 표시 이름
 * @param address 서버 주소
 * @param port 서버 포트 번호
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param protocol 이 항목이 쓰는 프로토콜. 나머지 프로토콜별 컬럼의 의미를 정한다
 * @param encoding FTP 제어 연결의 문자 인코딩
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
    @ColumnInfo(defaultValue = PROTOCOL_FTP) val protocol: String,
    @ColumnInfo val encoding: String
)
