package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository

/**
 * Opens a file with an appropriate external app, or navigates into it if it is a directory.
 * @param nav 디렉터리 탐색에 사용하는 FileNavigatorRepository 구현체
 * @param openFile 파일 경로를 받아 외부 앱으로 여는 콜백
 */
class OpenFileUseCase(
    private val nav: FileNavigatorRepository,
    private val openFile: (String) -> Unit
) {
    suspend fun open(item: FileHolderItem) {
        if (item.isDirectory) nav.navigate(item.path)
        else openFile(item.path)
    }
}
