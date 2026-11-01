package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

/** [FileType.of]가 폴더 여부와 확장자로 항목 종류를 정하는지 검증하는 단위 테스트. */
class FileTypeTest {

    @Test
    fun `폴더는 이름의 확장자와 무관하게 폴더다`() {
        assertEquals(FileType.Folder, FileType.of("backup.zip", isDirectory = true))
    }

    @Test
    fun `알려진 확장자는 해당 종류다`() {
        assertEquals(FileType.Image, FileType.of("photo.jpg", isDirectory = false))
        assertEquals(FileType.Video, FileType.of("clip.mp4", isDirectory = false))
        assertEquals(FileType.Audio, FileType.of("song.flac", isDirectory = false))
        assertEquals(FileType.Document, FileType.of("notes.txt", isDirectory = false))
        assertEquals(FileType.Pdf, FileType.of("paper.pdf", isDirectory = false))
        assertEquals(FileType.Archive, FileType.of("data.tar.gz", isDirectory = false))
        assertEquals(FileType.Apk, FileType.of("app.apk", isDirectory = false))
    }

    @Test
    fun `확장자는 대소문자를 구분하지 않는다`() {
        assertEquals(FileType.Pdf, FileType.of("PAPER.PDF", isDirectory = false))
        assertEquals(FileType.Image, FileType.of("IMG_0001.JPG", isDirectory = false))
    }

    @Test
    fun `확장자가 없거나 모르는 확장자면 일반 파일이다`() {
        assertEquals(FileType.Other, FileType.of("README", isDirectory = false))
        assertEquals(FileType.Other, FileType.of("data.bin", isDirectory = false))
    }

    @Test
    fun `숨김 파일의 첫 점은 확장자가 아니다`() {
        assertEquals(FileType.Other, FileType.of(".zip", isDirectory = false))
        assertEquals(FileType.Image, FileType.of(".cover.png", isDirectory = false))
    }
}
