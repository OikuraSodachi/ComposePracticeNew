package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageInput
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RemoteStorageUseCase]가 입력을 규칙(이름·주소 필수, 포트 1~65535)으로 검증하고, 유효한 입력만 저장하는지 검증하는 단위 테스트. */
class RemoteStorageInputValidationTest {

    /** 저장 요청을 기록하는 가짜 저장소. */
    private val repo = RecordingRemoteStorageRepository()
    /** 검증 대상. */
    private val useCase = RemoteStorageUseCase(repo, NoOpLastConnectionRepository)

    @Test
    fun `이름·주소가 있고 포트가 범위 안이면 유효하다`() {
        assertTrue(useCase.validateInput(input(name = "nas", address = "192.168.0.1", port = "21")).isEmpty())
    }

    @Test
    fun `아이디·비밀번호와 무관하게 이름·주소·포트만 검증한다`() {
        assertTrue(useCase.validateInput(input(name = "n", address = "a", port = "65535")).isEmpty())
    }

    @Test
    fun `공백뿐인 이름과 주소는 거부한다`() {
        assertEquals(
            setOf(RemoteStorageInputError.NAME_BLANK, RemoteStorageInputError.ADDRESS_BLANK),
            useCase.validateInput(input(name = "  ", address = "", port = "21"))
        )
    }

    @Test
    fun `포트는 1~65535 정수만 허용한다`() {
        listOf("0", "65536", "-1", "abc", "", " 21").forEach { port ->
            assertEquals(
                "port=\"$port\"",
                setOf(RemoteStorageInputError.PORT_OUT_OF_RANGE),
                useCase.validateInput(input(name = "n", address = "a", port = port))
            )
        }
        listOf("1", "21", "65535").forEach { port ->
            assertTrue("port=\"$port\"", useCase.validateInput(input(name = "n", address = "a", port = port)).isEmpty())
        }
    }

    @Test
    fun `유효한 입력은 포트를 숫자로 바꾸고 id와 프로토콜 설정을 유지해 저장한다`() {
        val settings = RemoteProtocolSettings.Ftp("EUC-KR")

        runBlocking { useCase.save(input(id = 7, name = "nas", address = "a", port = "2121", settings = settings)) }

        assertEquals(
            listOf(RemoteStorageItem(id = 7, name = "nas", address = "a", port = 2121, userId = "u", password = "p", settings = settings)),
            repo.inserted
        )
    }

    @Test
    fun `규칙을 위반한 입력은 저장하지 않는다`() {
        listOf(input(name = "", address = "a", port = "21"), input(name = "n", address = "a", port = "70000")).forEach {
            runBlocking { useCase.save(it) }
        }

        assertEquals(emptyList<RemoteStorageItem>(), repo.inserted)
    }

    /**
     * 검증에 쓸 입력값을 만든다.
     * @param id 항목 id
     * @param name 서버 별칭
     * @param address 서버 주소
     * @param port 포트 문자열
     * @param settings 프로토콜 설정
     * @return 입력값
     */
    private fun input(
        id: Long = 0,
        name: String,
        address: String,
        port: String,
        settings: RemoteProtocolSettings = RemoteProtocolSettings.Ftp("UTF-8")
    ) = RemoteStorageInput(id = id, name = name, address = address, port = port, userId = "u", password = "p", settings = settings)

    /** 저장 요청을 기록하는 테스트용 저장소. */
    private class RecordingRemoteStorageRepository : RemoteStorageRepository {
        /** [insert]로 받은 항목. */
        val inserted = mutableListOf<RemoteStorageItem>()

        override fun getAll(): Flow<List<RemoteStorageItem>> = emptyFlow()
        override suspend fun insert(item: RemoteStorageItem) { inserted += item }
        override suspend fun delete(item: RemoteStorageItem) {}
        override suspend fun getById(id: Long): RemoteStorageItem? = null
    }

    /** 검증과 저장이 마지막 연결 정보를 쓰지 않음을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnectionRepository : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }
}
