package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IndeterminatePhaseModel
import com.todokanai.composepracticenew.model.KnownSizeProgressModel
import com.todokanai.composepracticenew.ui.model.UiText
import com.todokanai.composepracticenew.ui.model.knownPartTextOf
import com.todokanai.composepracticenew.ui.model.phaseTextOf
import com.todokanai.composepracticenew.ui.model.unknownFilesTextOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 진행 다이얼로그와 진행 알림이 함께 쓰는 진행 문구(#464)가 맞는 문자열 리소스와 인자로 연결되는지 검증하는 단위 테스트.
 */
class ProgressTextTest {

    /** 크기를 아는 부분과 모르는 부분의 진행 예시. */
    private val knownSize = KnownSizeProgressModel(
        progress = 40,
        writtenSize = "4 KB",
        totalSize = "10 KB",
        unknownFileCount = 2,
        unknownWrittenSize = "3 KB"
    )

    @Test
    fun `준비 단계는 찾은 항목 수를 넣은 준비 문구다`() {
        assertEquals(
            UiText.Resource(R.string.progress_preparing, listOf(7)),
            phaseTextOf(IndeterminatePhaseModel.Preparing(foundEntries = 7, currentName = "a.txt"))
        )
    }

    @Test
    fun `비밀번호 확인 단계는 인자 없는 확인 문구다`() {
        assertEquals(
            UiText.Resource(R.string.progress_checking_password),
            phaseTextOf(IndeterminatePhaseModel.CheckingPassword(archiveName = "a.zip"))
        )
    }

    @Test
    fun `아는 부분 문구는 퍼센트를 넣는다`() {
        assertEquals(UiText.Resource(R.string.progress_known_part, listOf(40)), knownPartTextOf(40))
    }

    @Test
    fun `모르는 파일 문구는 작업 종류로 리소스를 고르고 파일 수와 처리한 크기를 넣는다`() {
        val expected: Map<FileOperationType?, Int> = mapOf(
            FileOperationType.DOWNLOAD to R.string.progress_unknown_files_received,
            FileOperationType.UPLOAD to R.string.progress_unknown_files_sent,
            FileOperationType.COPY to R.string.progress_unknown_files_processed,
            FileOperationType.MOVE to R.string.progress_unknown_files_processed,
            FileOperationType.DELETE to R.string.progress_unknown_files_processed,
            FileOperationType.COMPRESS to R.string.progress_unknown_files_processed,
            FileOperationType.UNZIP to R.string.progress_unknown_files_processed,
            null to R.string.progress_unknown_files_processed
        )
        assertEquals(FileOperationType.entries.toSet<FileOperationType?>() + null, expected.keys)
        expected.forEach { (type, res) ->
            assertEquals("$type", UiText.Resource(res, listOf(2, "3 KB")), unknownFilesTextOf(knownSize, type))
        }
    }
}
