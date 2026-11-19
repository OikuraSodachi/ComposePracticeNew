package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.viewmodel.ArchiveNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ArchiveNaming]이 압축 파일의 위치·기본 이름·검증을 정하는지 검증하는 단위 테스트(#450).
 * 위치는 첫 항목의 부모 폴더이고, 기본 이름은 하나면 그 항목의 이름(파일은 확장자 제외), 여러 개면 항목들이 있는 폴더의 이름이다.
 */
class ArchiveNamingTest {

    /** 목록 조회 상태를 정하지 않는 탐색 저장소 — 목록이 없으므로 이름 제안은 겹침 번호 없이 기본 이름을 낸다. */
    private val nav = FakeNavigator()

    /** '/' 구분 경로의 부모·표시 이름 규칙. "/"는 부모가 없고 이름이 비어 있는 루트다. */
    private val paths = object : StoragePathRules {
        override fun getParentPath(path: String): String? =
            if (path == "/") null else path.substringBeforeLast('/').ifEmpty { "/" }
        override fun displayNameOf(path: String): String = path.substringAfterLast('/')
    }

    /** 이름 규칙에 넘어온 (이름, 폴더, 형식). */
    private val validated = mutableListOf<Triple<String, String, ArchiveFormat>>()

    /** 이름 규칙 호출을 기록하는 람다로 만든 검증 대상. */
    private val naming = ArchiveNaming(
        validateArchiveName = { name, folder, format -> validated += Triple(name, folder, format); setOf(FileNameError.BLANK) },
        conflictCheckUseCase = ConflictCheckUseCase(RecordingOperationNotices(), ExactNameMatching),
        folder = FileNavigatorUseCase(nav, paths),
    )

    @Test
    fun `압축 파일은 첫 항목의 부모 폴더에 만든다`() {
        assertEquals("/storage/0/music", naming.folderOf(listOf(file("/storage/0/music/a.mp3"), file("/storage/0/b.mp3"))))
    }

    @Test
    fun `항목이 없거나 첫 항목의 부모를 정할 수 없으면 만들 폴더가 없다`() {
        assertNull(naming.folderOf(emptyList()))
        assertNull(naming.folderOf(listOf(folder("/"))))
    }

    @Test
    fun `항목이 하나면 그 이름을 제안하고 파일은 확장자를 뗀다`() {
        assertEquals("song", naming.suggestName(listOf(file("/storage/0/music/song.mp3"))))
        assertEquals("v1.2", naming.suggestName(listOf(folder("/storage/0/v1.2"))))
    }

    @Test
    fun `항목이 여러 개면 항목들이 있는 폴더의 이름을 제안한다`() {
        assertEquals("music", naming.suggestName(listOf(file("/storage/0/music/a.mp3"), file("/storage/0/music/b.mp3"))))
    }

    @Test
    fun `여러 항목이 이름 없는 루트에 있으면 첫 항목의 이름을 제안한다`() {
        assertEquals("a", naming.suggestName(listOf(file("/a.txt"), file("/b.txt"))))
    }

    @Test
    fun `항목이 없으면 빈 이름을 제안한다`() {
        assertEquals("", naming.suggestName(emptyList()))
    }

    @Test
    fun `이름 검증은 대상 폴더와 ZIP 형식을 이름 규칙에 넘기고 그 결과를 돌려준다`() {
        val errors = naming.validateName(targetFolder = "/storage/0/music", name = "songs")

        assertEquals(listOf(Triple("songs", "/storage/0/music", ArchiveFormat.ZIP)), validated)
        assertEquals(setOf(FileNameError.BLANK), errors)
    }

    /** [path]의 파일 항목. */
    private fun file(path: String) = item(path, isDirectory = false)

    /** [path]의 폴더 항목. */
    private fun folder(path: String) = item(path, isDirectory = true)

    /** [path]의 항목. */
    private fun item(path: String, isDirectory: Boolean) = FileHolderItem(
        path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), size = "", lastModified = "",
        sizeBytes = null, lastModifiedMillis = 0L, isLocal = true
    )
}
