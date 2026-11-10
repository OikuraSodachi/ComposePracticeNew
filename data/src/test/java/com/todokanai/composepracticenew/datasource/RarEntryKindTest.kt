package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RAR 산정·나열·추출이 함께 쓰는 항목 판별 [rarEntryKindOf]를 검증하는 단위 테스트(#315 리뷰).
 * RAR 라이브러리는 Android용 네이티브만 담아 JVM에서 열 수 없으므로, 속성 값으로 판별만 검증한다.
 */
class RarEntryKindTest {

    @Test
    fun `폴더 항목은 경로가 있어도 폴더로 판별한다`() {
        assertEquals(RarEntryKind.Folder, rarEntryKindOf(true, "dir"))
    }

    @Test
    fun `경로가 없는 항목은 이름 없음으로 판별한다`() {
        assertEquals(RarEntryKind.Unnamed, rarEntryKindOf(false, null))
        assertEquals(RarEntryKind.Unnamed, rarEntryKindOf(null, 1))
    }

    @Test
    fun `폴더가 아닌 항목은 경로를 슬래시 구분으로 바꿔 추출 대상으로 판별한다`() {
        assertEquals(RarEntryKind.Extracted("dir/a.txt"), rarEntryKindOf(false, "dir\\a.txt"))
        assertEquals(RarEntryKind.Extracted("a.txt"), rarEntryKindOf(null, "a.txt"))
    }
}
