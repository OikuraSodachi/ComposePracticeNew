package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageInput
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 원격 스토리지 접속 정보의 조회·추가·수정·삭제를 담당하는 UseCase.
 * @param repo 원격 스토리지 접속 정보를 읽고 쓰는 RemoteStorageRepository 구현체
 * @param lastConnectionRepo 삭제한 서버를 가리키는 마지막 접속 서버 기록을 지우는 LastConnectionRepository 구현체
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
     * @param input 사용자가 입력한 접속 정보
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 입력이다
     */
    fun validateInput(input: RemoteStorageInput): Set<RemoteStorageInputError> = buildSet {
        if (input.name.isBlank()) add(RemoteStorageInputError.NAME_BLANK)
        if (input.address.isBlank()) add(RemoteStorageInputError.ADDRESS_BLANK)
        if (!isValidPort(input.port)) add(RemoteStorageInputError.PORT_OUT_OF_RANGE)
    }

    /**
     * 입력한 접속 정보를 저장한다. id가 0이면 새 항목을 추가하고, 기존 id면 그 항목을 교체한다.
     * [validateInput]을 위반한 입력은 저장하지 않는다 — 화면은 같은 검증으로 확인 버튼을 막으므로, 입력과 확인이 겹친 순간에만 일어난다.
     * @param input 사용자가 입력한 접속 정보
     * @return Unit
     */
    suspend fun save(input: RemoteStorageInput) {
        if (validateInput(input).isEmpty()) repo.insert(itemOf(input))
    }

    /**
     * 원격 스토리지 접속 정보를 삭제하고, 그 서버가 마지막 연결 서버였으면 기록도 지운다.
     * 지운 id는 새 항목에 다시 쓰일 수 있으므로, 남겨 두면 재연결이 다른 서버로 이어진다.
     * 확인과 초기화는 저장소의 한 번의 저장 안에서 처리되어, 앞서 기록된 마지막 연결 서버를 기준으로 판단한다.
     * @param item 삭제할 RemoteStorageItem
     * @return Unit
     */
    suspend fun delete(item: RemoteStorageItem) {
        repo.delete(item)
        lastConnectionRepo.clearLastRemoteIdIf(item.id)
    }

    /** [port]가 [RemoteStorageItem.PORT_RANGE] 안의 정수인지 판별한다. */
    private fun isValidPort(port: String): Boolean = port.toIntOrNull()?.let { it in RemoteStorageItem.PORT_RANGE } ?: false

    /** [validateInput]을 통과한 [input]을 저장할 항목으로 바꾼다. */
    private fun itemOf(input: RemoteStorageInput) = RemoteStorageItem(
        id = input.id,
        name = input.name,
        address = input.address,
        port = input.port.toInt(),
        userId = input.userId,
        password = input.password,
        settings = input.settings
    )
}
