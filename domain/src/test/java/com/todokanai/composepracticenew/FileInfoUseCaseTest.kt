package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [FileInfoUseCase]가 목록 조회 때의 값을 쓰지 않고 모든 항목을 저장소에서 다시 읽는지 검증하는 단위 테스트. */
class FileInfoUseCaseTest {

    @Test
    fun `목록에 크기가 있는 파일도 저장소에서 다시 잰다`() = runBlocking {
        val repo = FakeFileInfoRepository(measurement = IoResult.Success(SizeMeasurement.Complete(999)))

        val result = FileInfoUseCase(repo).measureSize(listOf(file("/a", 10), dir("/d"), file("/u", null)))

        assertEquals(IoResult.Success(SizeMeasurement.Complete(999)), result)
        assertEquals(listOf(listOf("/a", "/d", "/u")), repo.measuredPaths)
    }

    @Test
    fun `측정이 하한이거나 실패면 그대로 돌려준다`() = runBlocking {
        val lowerBound = IoResult.Success(SizeMeasurement.LowerBound(100, listOf(FailedItem("/d/x", null))))
        assertEquals(lowerBound, FileInfoUseCase(FakeFileInfoRepository(measurement = lowerBound)).measureSize(listOf(dir("/d"))))

        val failed = IoResult.ItemFailed("원인")
        assertEquals(failed, FileInfoUseCase(FakeFileInfoRepository(measurement = failed)).measureSize(listOf(dir("/d"))))
    }

    @Test
    fun `속성은 목록의 값이 아니라 저장소에서 읽은 값이다`() = runBlocking {
        val read = IoResult.Success(ItemAttributes(isDirectory = true, lastModifiedMillis = 42))
        val repo = FakeFileInfoRepository(attributes = read)

        assertEquals(read, FileInfoUseCase(repo).readAttributes(file("/a", 10)))
        assertEquals(listOf("/a"), repo.attributePaths)
    }

    private fun file(path: String, size: Long?) = FileHolderItem(path, isDirectory = false, name = path, sizeBytes = size, lastModifiedMillis = 0)

    private fun dir(path: String) = FileHolderItem(path, isDirectory = true, name = path, sizeBytes = null, lastModifiedMillis = 0)

    /** 정해 둔 결과를 돌려주고, 읽기를 요청받은 경로를 기록하는 저장소. */
    private class FakeFileInfoRepository(
        private val measurement: IoResult<SizeMeasurement> = IoResult.Success(SizeMeasurement.Complete(0)),
        private val attributes: IoResult<ItemAttributes> = IoResult.ItemFailed(null)
    ) : FileInfoRepository {
        val measuredPaths = mutableListOf<List<String>>()
        val attributePaths = mutableListOf<String>()

        override fun locationOf(path: String): String? = null

        override suspend fun readAttributes(path: String): IoResult<ItemAttributes> {
            attributePaths += path
            return attributes
        }

        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> {
            measuredPaths += paths
            return measurement
        }

        override suspend fun countChildren(path: String): IoResult<Int> = IoResult.Success(0)
    }
}
