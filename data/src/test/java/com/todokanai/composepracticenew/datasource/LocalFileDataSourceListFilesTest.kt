package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [LocalFileDataSource.listFiles]가 읽지 못한 디렉터리를 빈 폴더로 바꾸지 않고 실패 결과로 알리는지 검증하는 단위 테스트(#215). */
class LocalFileDataSourceListFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dataSource = LocalFileDataSource()

    @Test
    fun `빈 디렉터리는 빈 목록이다`() {
        val dir = temp.newFolder("empty")

        assertEquals(IoResult.Success(emptyList<Any>()), dataSource.listFiles(dir.absolutePath))
    }

    @Test
    fun `디렉터리 항목을 반환한다`() {
        val dir = temp.newFolder("d")
        dir.resolve("a.txt").writeText("a")

        assertEquals(listOf("a.txt"), (dataSource.listFiles(dir.absolutePath) as IoResult.Success).value.map { it.name })
    }

    @Test
    fun `없는 경로는 빈 목록 대신 실패다`() {
        val missing = temp.root.resolve("missing")

        assertTrue(dataSource.listFiles(missing.absolutePath) is IoResult.ItemFailed)
    }

    @Test
    fun `디렉터리가 아닌 경로는 빈 목록 대신 실패다`() {
        val file = temp.newFile("f.txt")

        assertTrue(dataSource.listFiles(file.absolutePath) is IoResult.ItemFailed)
    }
}
