package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [FileInfoUseCase.measureSize]가 목록에 크기가 있는 파일은 그 값을 쓰고, 크기가 없는 항목만 저장소에서 측정해 더하는지 검증하는 단위 테스트. */
class FileInfoUseCaseTest {

    @Test
    fun `크기가 모두 목록에 있으면 저장소를 읽지 않는다`() = runBlocking {
        val repo = FakeFileInfoRepository(IoResult.Success(SizeMeasurement.Complete(999)))

        val result = FileInfoUseCase(repo).measureSize(listOf(file("/a", 10), file("/b", 20)))

        assertEquals(IoResult.Success(SizeMeasurement.Complete(30)), result)
        assertEquals(emptyList<List<String>>(), repo.measuredPaths)
    }

    @Test
    fun `폴더와 크기 미확인 파일만 측정하고 목록의 크기를 더한다`() = runBlocking {
        val repo = FakeFileInfoRepository(IoResult.Success(SizeMeasurement.Complete(100)))

        val result = FileInfoUseCase(repo).measureSize(listOf(file("/a", 10), dir("/d"), file("/u", null)))

        assertEquals(IoResult.Success(SizeMeasurement.Complete(110)), result)
        assertEquals(listOf(listOf("/d", "/u")), repo.measuredPaths)
    }

    @Test
    fun `측정이 하한이면 목록의 크기를 더해도 하한으로 남는다`() = runBlocking {
        val unreadable = listOf(FailedItem("/d/x", null))
        val repo = FakeFileInfoRepository(IoResult.Success(SizeMeasurement.LowerBound(100, unreadable)))

        val result = FileInfoUseCase(repo).measureSize(listOf(file("/a", 10), dir("/d")))

        assertEquals(IoResult.Success(SizeMeasurement.LowerBound(110, unreadable)), result)
    }

    @Test
    fun `측정 실패는 그대로 돌려준다`() = runBlocking {
        val repo = FakeFileInfoRepository(IoResult.ItemFailed("원인"))

        assertEquals(IoResult.ItemFailed("원인"), FileInfoUseCase(repo).measureSize(listOf(dir("/d"))))
    }

    private fun file(path: String, size: Long?) = FileHolderItem(path, isDirectory = false, name = path, sizeBytes = size, lastModifiedMillis = 0)

    private fun dir(path: String) = FileHolderItem(path, isDirectory = true, name = path, sizeBytes = null, lastModifiedMillis = 0)

    /** 정해 둔 측정 결과를 돌려주고, 측정을 요청받은 경로를 기록하는 저장소. */
    private class FakeFileInfoRepository(private val measurement: IoResult<SizeMeasurement>) : FileInfoRepository {
        val measuredPaths = mutableListOf<List<String>>()

        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> {
            measuredPaths += paths
            return measurement
        }

        override suspend fun countChildren(path: String): IoResult<Int> = IoResult.Success(0)
    }
}
