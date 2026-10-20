package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RemoteStorageUseCase.validateInput]의 원격 스토리지 입력 검증 규칙(이름·주소 필수, 포트 1~65535)을 검증하는 단위 테스트. */
class RemoteStorageInputValidationTest {

    private val useCase = RemoteStorageUseCase(NoOpRemoteStorageRepository, NoOpLastConnectionRepository)

    @Test
    fun `이름·주소가 있고 포트가 범위 안이면 유효하다`() {
        assertTrue(useCase.validateInput(name = "nas", address = "192.168.0.1", port = "21").isEmpty())
    }

    @Test
    fun `아이디·비밀번호와 무관하게 이름·주소·포트만 검증한다`() {
        assertTrue(useCase.validateInput(name = "n", address = "a", port = "65535").isEmpty())
    }

    @Test
    fun `공백뿐인 이름과 주소는 거부한다`() {
        assertEquals(
            setOf(RemoteStorageInputError.NAME_BLANK, RemoteStorageInputError.ADDRESS_BLANK),
            useCase.validateInput(name = "  ", address = "", port = "21")
        )
    }

    @Test
    fun `포트는 1~65535 정수만 허용한다`() {
        listOf("0", "65536", "-1", "abc", "", " 21").forEach { port ->
            assertEquals(
                "port=\"$port\"",
                setOf(RemoteStorageInputError.PORT_OUT_OF_RANGE),
                useCase.validateInput(name = "n", address = "a", port = port)
            )
        }
        listOf("1", "21", "65535").forEach { port ->
            assertTrue("port=\"$port\"", useCase.validateInput(name = "n", address = "a", port = port).isEmpty())
        }
    }

    /** validateInput이 저장소를 쓰지 않음을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpRemoteStorageRepository : RemoteStorageRepository {
        override fun getAll(): Flow<List<RemoteStorageItem>> = emptyFlow()
        override suspend fun insert(item: RemoteStorageItem) {}
        override suspend fun delete(item: RemoteStorageItem) {}
        override suspend fun getById(id: Long): RemoteStorageItem? = null
    }

    /** validateInput이 마지막 연결 정보를 쓰지 않음을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnectionRepository : LastConnectionRepository {
        override suspend fun lastRemoteId(): Long? = null
        override fun setLastRemoteId(id: Long?) {}
    }
}
