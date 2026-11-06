package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.ui.model.operationLabelRes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 진행 다이얼로그와 진행 알림이 함께 쓰는 작업 이름 문구가 작업 종류마다 맞는 문자열 리소스로 연결되는지 검증하는 단위 테스트.
 * 누락은 exhaustive `when`이 컴파일 단계에서 잡으므로, 여기서는 잘못 연결된 리소스(예: 이동에 복사 문구)를 잡는다.
 */
class OperationLabelTest {

    /** 작업 종류별로 기대하는 문자열 리소스. 종류를 모르면(null) 일반 문구를 쓴다. */
    private val expected: Map<FileOperationType?, Int> = mapOf(
        FileOperationType.COPY to R.string.progress_copying,
        FileOperationType.MOVE to R.string.progress_moving,
        FileOperationType.DELETE to R.string.progress_deleting,
        FileOperationType.ZIP to R.string.progress_zipping,
        FileOperationType.UNZIP to R.string.progress_unzipping,
        FileOperationType.DOWNLOAD to R.string.progress_downloading,
        FileOperationType.UPLOAD to R.string.progress_uploading,
        null to R.string.progress_processing
    )

    @Test
    fun `작업 종류마다 그 작업의 문구를 고른다`() {
        expected.forEach { (type, res) ->
            assertEquals("$type", res, operationLabelRes(type))
        }
    }

    @Test
    fun `모든 작업 종류의 기대 문구를 확인한다`() {
        assertEquals(FileOperationType.entries.toSet<FileOperationType?>() + null, expected.keys)
    }
}
