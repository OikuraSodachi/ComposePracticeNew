package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * RAR 산정·나열·추출이 함께 쓰는 항목 판별 [rarEntryKindOf]를 검증하는 단위 테스트(#315 리뷰, #235, #317).
 * RAR 라이브러리는 Android용 네이티브만 담아 JVM에서 열 수 없으므로, 속성 값으로 판별만 검증한다.
 */
class RarEntryKindTest {

    @Test
    fun `폴더 항목은 경로를 슬래시 구분으로 바꿔 폴더로 판별한다`() {
        assertEquals(RarEntryKind.Folder("dir/sub"), rarEntryKindOf(true, "dir\\sub"))
    }

    @Test
    fun `경로가 없는 항목은 폴더 여부와 관계없이 읽지 못하는 아카이브로 알린다`() {
        assertThrows(IOException::class.java) { rarEntryKindOf(false, null) }
        assertThrows(IOException::class.java) { rarEntryKindOf(true, null) }
        assertThrows(IOException::class.java) { rarEntryKindOf(null, 1) }
    }

    @Test
    fun `폴더가 아닌 항목은 경로를 슬래시 구분으로 바꿔 파일로 판별한다`() {
        assertEquals(RarEntryKind.File("dir/a.txt"), rarEntryKindOf(false, "dir\\a.txt"))
        assertEquals(RarEntryKind.File("a.txt"), rarEntryKindOf(null, "a.txt"))
    }
}
