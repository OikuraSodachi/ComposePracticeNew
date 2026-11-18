package com.todokanai.composepracticenew.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [RemoteStorageInfo.protocol]에 저장하는 프로토콜. Room이 상수 이름을 TEXT로 저장하므로 상수 이름을 바꾸지 않는다.
 * 저장 값을 닫힌 타입으로 두어, 프로토콜을 추가하면 읽는 쪽 `when`도 컴파일 오류로 처리할 곳을 알려 준다(#457).
 */
enum class StoredProtocol { FTP }

/** 컬럼 기본값·마이그레이션 SQL에 쓰는 [StoredProtocol.FTP]의 저장 값. 애너테이션 인자는 상수여야 해서 따로 둔다. */
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
    @ColumnInfo(defaultValue = PROTOCOL_FTP) val protocol: StoredProtocol,
    @ColumnInfo val encoding: String
)
