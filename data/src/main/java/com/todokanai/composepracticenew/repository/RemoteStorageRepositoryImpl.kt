package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.room.PROTOCOL_FTP
import com.todokanai.composepracticenew.data.room.RemoteStorageInfo
import com.todokanai.composepracticenew.data.room.RemoteStorageInfoDao
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 스토리지 접속 정보의 Room 기반 구현체. */
@Singleton
class RemoteStorageRepositoryImpl @Inject constructor(
    private val dao: RemoteStorageInfoDao
) : RemoteStorageRepository {

    override fun getAll(): Flow<List<RemoteStorageItem>> =
        dao.getAll().map { list -> list.map { it.toItem() } }

    override suspend fun insert(item: RemoteStorageItem) =
        dao.insert(item.toEntity())

    override suspend fun delete(item: RemoteStorageItem) =
        dao.delete(item.toEntity())

    override suspend fun getById(id: Long): RemoteStorageItem? =
        dao.getById(id)?.toItem()
}

private fun RemoteStorageInfo.toItem() = RemoteStorageItem(
    id = id,
    name = name,
    address = address,
    port = port,
    userId = userId,
    password = password,
    settings = settings()
)

/** 저장된 프로토콜 값을 설정으로 바꾼다. 이 값은 [toEntity]만 쓰므로 모르는 값은 구현 오류다. */
private fun RemoteStorageInfo.settings(): RemoteProtocolSettings = when (protocol) {
    PROTOCOL_FTP -> RemoteProtocolSettings.Ftp(encoding)
    else -> error("알 수 없는 프로토콜: $protocol")
}

private fun RemoteStorageItem.toEntity() = when (val settings = settings) {
    is RemoteProtocolSettings.Ftp -> RemoteStorageInfo(
        id = id,
        name = name,
        address = address,
        port = port,
        userId = userId,
        password = password,
        protocol = PROTOCOL_FTP,
        encoding = settings.encoding
    )
}
