package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType

/**
 * 퍼센트 없이 보이는 단계의 문구를 고른다. 진행 다이얼로그와 진행 알림이 같은 문구를 쓰도록 한 곳에서 정한다(#464).
 * @param phase 단계
 * @return 표시 문구
 */
fun phaseTextOf(phase: IndeterminatePhaseModel): UiText = when (phase) {
    is IndeterminatePhaseModel.Preparing -> UiText.Resource(R.string.progress_preparing, listOf(phase.foundEntries))
    is IndeterminatePhaseModel.CheckingPassword -> UiText.Resource(R.string.progress_checking_password)
}

/**
 * 크기를 모르는 파일이 섞인 작업에서 크기를 아는 부분의 진행 문구를 만든다. 진행 다이얼로그와 진행 알림이 같은 문구를 쓰도록 한 곳에서 정한다(#464).
 * @param progress 크기를 아는 부분의 진행도(0~100 퍼센트)
 * @return 표시 문구
 */
fun knownPartTextOf(progress: Int): UiText = UiText.Resource(R.string.progress_known_part, listOf(progress))

/**
 * 크기를 모르는 파일이 섞인 작업에서 크기를 모르는 파일의 처리 문구를 만든다. 진행 다이얼로그와 진행 알림이 같은 문구를 쓰도록 한 곳에서 정한다(#464).
 * @param knownSize 크기를 아는 부분과 모르는 부분의 진행
 * @param operationType 작업 종류 — 처리 문구(받음·보냄·처리)를 고른다
 * @return 표시 문구
 */
fun unknownFilesTextOf(knownSize: KnownSizeProgressModel, operationType: FileOperationType): UiText =
    UiText.Resource(unknownFilesTextRes(operationType), listOf(knownSize.unknownFileCount, knownSize.unknownWrittenSize))

/**
 * 크기를 모르는 파일의 처리 문구를 작업 종류로 고른다.
 * 작업 종류를 모두 나열해 새 종류가 생기면 컴파일 단계에서 드러나게 한다.
 * @param operationType 작업 종류
 * @return 문자열 리소스 ID
 */
@StringRes
private fun unknownFilesTextRes(operationType: FileOperationType): Int = when (operationType) {
    FileOperationType.DOWNLOAD -> R.string.progress_unknown_files_received
    FileOperationType.UPLOAD -> R.string.progress_unknown_files_sent
    FileOperationType.COPY, FileOperationType.MOVE, FileOperationType.DELETE, FileOperationType.REMOTE_DELETE,
    FileOperationType.COMPRESS, FileOperationType.UNZIP, FileOperationType.ARCHIVE_COPY, FileOperationType.ARCHIVE_ADD -> R.string.progress_unknown_files_processed
}
