package com.todokanai.composepracticenew.ui.model

import com.todokanai.fileexplorer.FileEntry

/** breadcrumb 경로 표시용 앱 레이어 모델. fileExplorer 모듈의 FileEntry를 앱 레이어에서 격리한다. */
data class DirectoryItem(
    val name: String,
    val path: String
) {
    companion object {
        /** fileExplorer 모듈의 FileEntry를 앱 레이어 모델로 변환한다. */
        fun from(entry: FileEntry) = DirectoryItem(
            name = entry.name,
            path = entry.path
        )
    }
}
