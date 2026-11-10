package com.todokanai.composepracticenew.datasource

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [ExtractionRoot]가 항목의 위치를 정규 경로로 정하고, 루트 밖이나 루트 자신을 가리키는 항목을 풀지 않는지 검증하는 단위 테스트(#315 리뷰). */
class ExtractionRootTest {

    /** 루트를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("extraction-root-test").toFile()
    /** 추출 대상 루트 디렉터리. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `루트를 정규 경로가 아닌 형태로 받아도 정규 경로의 위치를 돌려준다`() {
        val root = ExtractionRoot(File(dir, "dest/../dest"))

        assertEquals(File(dest.canonicalFile, "a/b.txt").canonicalFile, root.targetOf("a/b.txt"))
    }

    @Test
    fun `루트 밖을 가리키는 항목은 풀지 않는다`() {
        val root = ExtractionRoot(dest)

        assertNull(root.targetOf("../evil.txt"))
        assertNull(root.topLevelNameOf("a/../../evil.txt"))
    }

    @Test
    fun `루트 자신을 가리키는 항목은 풀지 않는다`() {
        assertNull(ExtractionRoot(dest).targetOf("a/.."))
    }

    @Test
    fun `루트 바로 아래에 생기는 이름은 경로를 정리한 뒤의 첫 성분이다`() {
        val root = ExtractionRoot(dest)

        assertEquals("a", root.topLevelNameOf("a/b/c.txt"))
        assertEquals("y.txt", root.topLevelNameOf("x/../y.txt"))
    }
}
