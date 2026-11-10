package com.todokanai.composepracticenew.compose.listview

import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 다중 선택 모드 더보기 메뉴의 항목 구성과 순서, 작업 항목이 넘기는 작업 종류를 검증하는 단위 테스트(#238). */
class MultiSelectMoreActionsTest {

    private val archive = item("/a.zip")
    private val other = item("/b.txt")

    /** 눌린 항목이 넘긴 로컬 작업을 기록한다. */
    private val selectedOperations = mutableListOf<SelectMode.LocalOperation>()

    /** 메뉴 항목이 다이얼로그를 여는 상태 홀더. */
    private val dialogState = BottomButtonDialogState()

    /** [selectedList], [canUnzip], 압축 파일을 만들 폴더 [archiveFolder]로 메뉴 항목을 만든다. */
    private fun actions(selectedList: List<FileHolderItem>, canUnzip: Boolean = false, archiveFolder: String? = "/") =
        multiSelectMoreActions(
            selectedList = selectedList,
            canUnzip = { canUnzip },
            archiveFolderOf = { archiveFolder },
            dialogState = dialogState,
            onSelectOperation = { selectedOperations += it },
            onShowInfo = {},
            onUpload = {},
        )

    @Test
    fun `여러 개를 선택하면 압축 정보 업로드만 보인다`() {
        assertEquals(
            listOf(R.string.btn_zip, R.string.btn_info, R.string.btn_upload),
            actions(listOf(archive, other), canUnzip = true).map { it.labelRes },
        )
    }

    @Test
    fun `압축 해제할 수 없는 항목 하나를 선택하면 이름 변경까지 보인다`() {
        assertEquals(
            listOf(R.string.btn_zip, R.string.btn_info, R.string.btn_upload, R.string.btn_rename),
            actions(listOf(other), canUnzip = false).map { it.labelRes },
        )
    }

    @Test
    fun `압축 해제할 수 있는 항목 하나를 선택하면 압축 해제 항목이 마지막에 보인다`() {
        assertEquals(
            listOf(R.string.btn_zip, R.string.btn_info, R.string.btn_upload, R.string.btn_rename, R.string.btn_unzip, R.string.btn_unzip_here),
            actions(listOf(archive), canUnzip = true).map { it.labelRes },
        )
    }

    @Test
    fun `압축 해제 항목은 각자의 작업 종류를 넘긴다`() {
        val menu = actions(listOf(archive), canUnzip = true)

        menu.first { it.labelRes == R.string.btn_unzip }.onClick()
        menu.first { it.labelRes == R.string.btn_unzip_here }.onClick()

        assertEquals(listOf(SelectMode.LocalOperation.Unzip, SelectMode.LocalOperation.UnzipHere), selectedOperations)
    }

    @Test
    fun `압축 항목은 정한 폴더로 압축 다이얼로그를 연다`() {
        actions(listOf(archive, other), archiveFolder = "/dir").first { it.labelRes == R.string.btn_zip }.onClick()

        assertEquals("/dir", dialogState.compressTarget)
    }

    @Test
    fun `압축 파일을 만들 폴더를 정할 수 없으면 압축 항목을 두지 않는다`() {
        assertEquals(
            listOf(R.string.btn_info, R.string.btn_upload),
            actions(listOf(archive, other), archiveFolder = null).map { it.labelRes },
        )
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
