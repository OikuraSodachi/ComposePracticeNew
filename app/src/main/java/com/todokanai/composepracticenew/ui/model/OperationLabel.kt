package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType

/**
 * 진행 중인 작업의 이름 문구("복사 중" 등)를 작업 종류로 고른다. 진행 다이얼로그와 진행 알림이 같은 문구를 쓰도록 한 곳에서 정한다.
 * 작업 종류를 모두 나열해 새 종류가 생기면 컴파일 단계에서 드러나게 한다.
 * @param type 작업 종류. 모르면 null
 * @return 문자열 리소스 ID
 */
@StringRes
fun operationLabelRes(type: FileOperationType?): Int = when (type) {
    FileOperationType.COPY -> R.string.progress_copying
    FileOperationType.MOVE -> R.string.progress_moving
    FileOperationType.DELETE -> R.string.progress_deleting
    FileOperationType.COMPRESS -> R.string.progress_compressing
    FileOperationType.UNZIP -> R.string.progress_unzipping
    FileOperationType.DOWNLOAD -> R.string.progress_downloading
    FileOperationType.UPLOAD -> R.string.progress_uploading
    null -> R.string.progress_processing
}
