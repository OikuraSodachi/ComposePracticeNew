package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 원격 스토리지 접속 정보의 조회·추가·수정·삭제를 담당하는 UseCase.
 * @param repo 원격 스토리지 접속 정보를 읽고 쓰는 RemoteStorageRepository 구현체
 * @param lastConnectionRepo 마지막 접속 서버 id를 저장하는 LastConnectionRepository 구현체
 */
class RemoteStorageUseCase(
    private val repo: RemoteStorageRepository,
    private val lastConnectionRepo: LastConnectionRepository
) {
    /**
     * 저장된 원격 스토리지 접속 정보 목록을 스트림으로 반환한다.
     * @return 원격 스토리지 항목 목록을 방출하는 Flow
     */
    fun getAll(): Flow<List<RemoteStorageItem>> = repo.getAll()

    /**
     * 원격 스토리지 접속 정보 입력값을 검증한다.
     * 이름·주소는 공백뿐일 수 없고, 포트는 1~65535 범위의 정수여야 한다. 아이디·비밀번호는 익명 접속을 위해 빈 값을 허용한다.
     * @param name 사용자가 입력한 서버 별칭
     * @param address 사용자가 입력한 서버 주소
     * @param port 사용자가 입력한 포트 문자열
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 입력이다
     */
    fun validateInput(name: String, address: String, port: String): Set<RemoteStorageInputError> = buildSet {
        if (name.isBlank()) add(RemoteStorageInputError.NAME_BLANK)
        if (address.isBlank()) add(RemoteStorageInputError.ADDRESS_BLANK)
        if (!isValidPort(port)) add(RemoteStorageInputError.PORT_OUT_OF_RANGE)
    }

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
     * @param item 갱신할 RemoteStorageItem — 유효한 기존 id 필요. id가 일치하는 행을 REPLACE함; id가 0이면 새 행이 INSERT됨
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
    fun saveLastConnectedId(id: Long?) = lastConnectionRepo.setLastRemoteId(id)

    /**
     * 삭제된 항목이 마지막 연결 서버인 경우 DataStore의 lastRemoteId를 초기화한다.
     * @param id 삭제된 항목의 서버 id
     * @return Unit
     */
    suspend fun clearLastConnectedIfMatches(id: Long) {
        // 알려진 한계(PR #199 리뷰 CLEANUP 2, 미해결): 읽기와 쓰기가 분리된 check-then-act다. setLastRemoteId는 fire-and-forget이라
        // 직전 saveLastConnectedId(id)가 아직 반영되지 않았으면 확인이 false가 되어 초기화를 건너뛰고, 이후 저장이 반영되어
        // 삭제된 서버 id가 남을 수 있다. 대응안: 확인과 삭제를 하나의 DataStore edit 트랜잭션으로 합친다(예: clearLastRemoteIdIf(id)).
        if (lastConnectionRepo.lastRemoteId() == id) lastConnectionRepo.setLastRemoteId(null)
    }

    /** [port]가 [PORT_RANGE] 안의 정수인지 판별한다. */
    private fun isValidPort(port: String): Boolean = port.toIntOrNull()?.let { it in PORT_RANGE } ?: false

    private companion object {
        /** TCP 포트로 허용하는 범위. 0은 예약 포트라 제외한다. */
        val PORT_RANGE = 1..65535
    }
}
