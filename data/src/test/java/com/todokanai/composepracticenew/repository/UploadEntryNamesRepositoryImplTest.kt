package com.todokanai.composepracticenew.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [UploadEntryNamesRepositoryImpl]이 폴더 업로드가 만들 이름을 폴더별로 읽는지 검증하는 단위 테스트. 링크·나열하지 못한 폴더는 LocalTreeSymlinkTest·LocalTreeUnlistedFolderTest가 검증한다. */
class UploadEntryNamesRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `폴더마다 바로 안 항목 이름을 올리는 폴더 이름부터 이은 표시용 경로와 함께 돌려주고 파일 경로는 건너뛴다(#323)`() = runTest {
        val root = temp.newFolder("photos")
        root.resolve("sub/deeper").mkdirs()
        root.resolve("x.jpg").writeText("x")
        root.resolve("sub/y.jpg").writeText("y")
        val file = temp.newFile("alone.txt")

        val result = UploadEntryNamesRepositoryImpl().folderEntryNames(listOf(root.absolutePath, file.absolutePath))

        assertEquals(
            mapOf("photos" to setOf("sub", "x.jpg"), "photos/sub" to setOf("deeper", "y.jpg")),
            result.associate { it.folderLabel to it.names.toSet() },
        )
    }
}
