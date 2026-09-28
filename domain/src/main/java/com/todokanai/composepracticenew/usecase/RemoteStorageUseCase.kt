package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LocalDataRepository
import kotlinx.coroutines.flow.Flow

/**
 * 원격 스토리지 접속 정보의 조회·추가·수정·삭제를 담당하는 UseCase.
 * @param repo 원격 스토리지 접속 정보를 읽고 쓰는 LocalDataRepository 구현체
 */
class RemoteStorageUseCase(private val repo: LocalDataRepository) {
    /**
     * 저장된 원격 스토리지 접속 정보 목록을 스트림으로 반환한다.
     * @return 원격 스토리지 항목 목록을 방출하는 Flow
     */
    fun getAll(): Flow<List<RemoteStorageItem>> = repo.getAll()

    /**
     * 새 원격 스토리지 접속 정보를 저장한다.
     * @param name 사용자가 지정한 서버 별칭
     * @param address 서버 주소 (IP 또는 호스트명)
     * @param port 서버 포트 번호
     * @param userId 로그인 사용자 아이디
     * @param password 로그인 비밀번호
     * @param encoding 서버 파일 이름 인코딩 (예: UTF-8)
     * @return Unit
     */
    suspend fun add(name: String, address: String, port: Int, userId: String, password: String, encoding: String) =
        repo.insert(RemoteStorageItem(name = name, address = address, port = port, userId = userId, password = password, encoding = encoding))

    /**
     * 기존 원격 스토리지 접속 정보를 갱신한다.
     * @param item 갱신할 RemoteStorageItem (id가 일치하는 항목을 덮어씀)
     * @return Unit
     */
    suspend fun update(item: RemoteStorageItem) = repo.insert(item)

    /**
     * 원격 스토리지 접속 정보를 삭제한다.
     * @param item 삭제할 RemoteStorageItem
     * @return Unit
     */
    suspend fun delete(item: RemoteStorageItem) = repo.delete(item)

    /**
     * 마지막으로 연결한 서버의 id를 DataStore에 저장한다.
     * @param id 저장할 서버 id, null이면 마지막 연결 정보를 초기화한다
     * @return Unit
     */
    fun saveLastConnectedId(id: Long?) = repo.setLastRemoteId(id)

    /**
     * 삭제된 항목이 마지막 연결 서버인 경우 DataStore의 lastRemoteId를 초기화한다.
     * @param id 삭제된 항목의 서버 id
     * @return Unit
     */
    suspend fun clearLastConnectedIfMatches(id: Long) {
        if (repo.lastRemoteId() == id) repo.setLastRemoteId(null)
    }

    /**
     * DataStore에 저장된 마지막 연결 서버 id로 RemoteStorageItem을 조회한다.
     * @return 마지막 연결 서버 정보, 저장된 id가 없거나 해당 항목이 삭제된 경우 null
     */
    suspend fun getLastConnectedItem(): RemoteStorageItem? {
        val id = repo.lastRemoteId() ?: return null
        return repo.getRemoteById(id)
    }
}
