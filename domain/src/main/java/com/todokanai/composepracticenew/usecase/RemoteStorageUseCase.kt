package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageInput
import com.todokanai.composepracticenew.model.RemoteStorageInputCheck
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import kotlinx.coroutines.flow.Flow

/**
 * 원격 스토리지 접속 정보의 조회·추가·수정·삭제를 담당하는 UseCase.
 * @param repo 원격 스토리지 접속 정보를 읽고 쓰는 RemoteStorageRepository 구현체
 */
class RemoteStorageUseCase(private val repo: RemoteStorageRepository) {
    /**
     * 저장된 원격 스토리지 접속 정보 목록을 스트림으로 반환한다.
     * @return 원격 스토리지 항목 목록을 방출하는 Flow
     */
    fun getAll(): Flow<List<RemoteStorageItem>> = repo.getAll()

    /**
     * 원격 스토리지 접속 정보 입력값을 검증한다.
     * 이름·주소는 공백뿐일 수 없고, 포트는 1~65535 범위의 정수여야 한다. 아이디·비밀번호는 익명 접속을 위해 빈 값을 허용한다.
     * @param input 사용자가 입력한 접속 정보
     * @return 통과하면 [save]에 넘길 [RemoteStorageInputCheck.Valid], 위반하면 위반한 규칙을 담은 [RemoteStorageInputCheck.Invalid]
     */
    fun validateInput(input: RemoteStorageInput): RemoteStorageInputCheck {
        val errors = violationsOf(input)
        return if (errors.isEmpty()) RemoteStorageInputCheck.Valid(itemOf(input)) else RemoteStorageInputCheck.Invalid(errors)
    }

    /**
     * 검증을 통과한 접속 정보를 저장한다. id가 0이면 새 항목을 추가하고, 기존 id면 그 항목을 교체한다.
     * [validateInput]이 만든 값만 받으므로 거부된 입력을 저장하지 않고 성공처럼 끝나는 경우가 없다(#366).
     * @param input [validateInput]을 통과한 입력
     * @return Unit
     */
    suspend fun save(input: RemoteStorageInputCheck.Valid) {
        repo.insert(input.item)
    }

    /**
     * 원격 스토리지 접속 정보를 삭제한다.
     * @param item 삭제할 RemoteStorageItem
     * @return Unit
     */
    suspend fun delete(item: RemoteStorageItem) {
        repo.delete(item)
    }

    /**
     * [input]이 위반한 규칙을 모은다.
     * @param input 사용자가 입력한 접속 정보
     * @return 위반한 규칙의 집합. 비어 있으면 모든 규칙을 통과했다
     */
    private fun violationsOf(input: RemoteStorageInput): Set<RemoteStorageInputError> = buildSet {
        if (input.name.isBlank()) add(RemoteStorageInputError.NAME_BLANK)
        if (input.address.isBlank()) add(RemoteStorageInputError.ADDRESS_BLANK)
        if (!isValidPort(input.port)) add(RemoteStorageInputError.PORT_OUT_OF_RANGE)
    }

    /**
     * [port]가 [RemoteStorageItem.PORT_RANGE] 안의 정수인지 판별한다.
     * @param port 사용자가 입력한 포트 문자열
     * @return 정수이고 범위 안이면 true
     */
    private fun isValidPort(port: String): Boolean = port.toIntOrNull()?.let { it in RemoteStorageItem.PORT_RANGE } ?: false

    /**
     * 규칙을 통과한 입력값을 저장할 항목으로 바꾼다.
     * @param input 규칙을 통과한 입력값
     * @return 저장할 항목
     */
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
