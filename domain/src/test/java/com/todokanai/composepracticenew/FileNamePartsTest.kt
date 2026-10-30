package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameParts
import org.junit.Assert.assertEquals
import org.junit.Test

/** [FileNameParts]의 확장자 경계와 번호 형식을 검증하는 단위 테스트. */
class FileNamePartsTest {

    @Test
    fun `확장자는 마지막 점부터다`() {
        assertEquals("archive.tar".length, FileNameParts.baseNameEnd("archive.tar.gz"))
    }

    @Test
    fun `첫 글자의 점은 숨김 파일 표시라 확장자가 아니다`() {
        assertEquals(".bashrc".length, FileNameParts.baseNameEnd(".bashrc"))
    }

    @Test
    fun `점이 없으면 이름 전체가 기본 이름이다`() {
        assertEquals("README".length, FileNameParts.baseNameEnd("README"))
    }

    @Test
    fun `번호 0은 원래 이름이고 그 밖에는 확장자 앞에 번호를 붙인다`() {
        assertEquals("x.zip", FileNameParts.numbered("x", ".zip", 0))
        assertEquals("x (3).zip", FileNameParts.numbered("x", ".zip", 3))
        assertEquals("New folder (1)", FileNameParts.numbered("New folder", "", 1))
    }
}
