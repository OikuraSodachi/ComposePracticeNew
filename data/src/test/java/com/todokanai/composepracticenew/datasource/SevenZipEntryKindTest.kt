package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * RAR 산정·나열·추출이 함께 쓰는 항목 판별 [sevenZipEntryKindOf]와 링크 판별 [isSevenZipLink]를 검증하는 단위 테스트(#315 리뷰, #235, #317, #424).
 * RAR 라이브러리는 Android용 네이티브만 담아 JVM에서 열 수 없으므로, 속성 값으로 판별만 검증한다.
 */
class SevenZipEntryKindTest {

    @Test
    fun `폴더 항목은 경로를 슬래시 구분으로 바꿔 폴더로 판별한다`() {
        assertEquals(SevenZipEntryKind.Folder("dir/sub"), sevenZipEntryKindOf(true, "dir\\sub", isLink = false))
    }

    @Test
    fun `경로가 없는 항목은 폴더 여부와 관계없이 읽지 못하는 아카이브로 알린다`() {
        assertThrows(IOException::class.java) { sevenZipEntryKindOf(false, null, isLink = false) }
        assertThrows(IOException::class.java) { sevenZipEntryKindOf(true, null, isLink = false) }
        assertThrows(IOException::class.java) { sevenZipEntryKindOf(null, 1, isLink = true) }
    }

    @Test
    fun `폴더가 아닌 항목은 경로를 슬래시 구분으로 바꿔 파일로 판별한다`() {
        assertEquals(SevenZipEntryKind.File("dir/a.txt"), sevenZipEntryKindOf(false, "dir\\a.txt", isLink = false))
        assertEquals(SevenZipEntryKind.File("a.txt"), sevenZipEntryKindOf(null, "a.txt", isLink = false))
    }

    @Test
    fun `링크 항목은 폴더로 표시돼 있어도 링크로 판별한다`() {
        assertEquals(SevenZipEntryKind.Link("dir/link"), sevenZipEntryKindOf(false, "dir\\link", isLink = true))
        assertEquals(SevenZipEntryKind.Link("junction"), sevenZipEntryKindOf(true, "junction", isLink = true))
    }

    @Test
    fun `심볼릭 링크나 하드 링크의 대상 경로가 있으면 링크다`() {
        assertTrue(isSevenZipLink(symLink = "target.txt", hardLink = null, posixAttrib = null, attributes = null))
        assertTrue(isSevenZipLink(symLink = null, hardLink = "dir/a.txt", posixAttrib = null, attributes = null))
    }

    @Test
    fun `Unix 모드가 심볼릭 링크면 링크다`() {
        assertTrue(isSevenZipLink(symLink = null, hardLink = null, posixAttrib = 0xA1FF, attributes = null))
    }

    @Test
    fun `Unix 확장 표시와 함께 위쪽 16비트에 링크 모드를 담은 속성이면 링크다`() {
        assertTrue(isSevenZipLink(symLink = null, hardLink = null, posixAttrib = null, attributes = (0xA1FF shl 16) or 0x8000))
    }

    @Test
    fun `Unix 확장 표시가 없으면 위쪽 16비트를 Unix 모드로 보지 않는다`() {
        assertFalse(isSevenZipLink(symLink = null, hardLink = null, posixAttrib = null, attributes = 0xA1FF shl 16))
    }

    @Test
    fun `일반 파일과 폴더의 속성이나 비어 있는 링크 대상은 링크가 아니다`() {
        assertFalse(isSevenZipLink(symLink = "", hardLink = "", posixAttrib = 0x81A4, attributes = (0x81A4 shl 16) or 0x8000))
        assertFalse(isSevenZipLink(symLink = null, hardLink = null, posixAttrib = 0x41ED, attributes = 0x10))
        assertFalse(isSevenZipLink(symLink = null, hardLink = null, posixAttrib = null, attributes = null))
    }
}
