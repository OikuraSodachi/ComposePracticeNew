package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 로컬 항목을 연다 — 내용을 볼 수 있는 압축 파일은 내용 보기 화면으로 가라고 요청하고, 그 밖(폴더 포함)은 [OpenFileUseCase]로 연다.
 * 목록 행과 검색 결과가 같은 규칙으로 열도록 두 ViewModel이 함께 쓴다.
 * @param openFileUseCase 폴더로 이동하고 파일을 다른 앱으로 여는 UseCase
 * @param archiveViewUseCase 내용 보기로 열 압축 파일인지 판정하는 UseCase
 * @param scope 파일 열기를 실행할 ViewModel 스코프
 */
class LocalItemOpener(
    private val openFileUseCase: OpenFileUseCase,
    private val archiveViewUseCase: ArchiveViewUseCase,
    private val scope: CoroutineScope,
) {
    private val _archiveViewRequests = Channel<String>(Channel.CONFLATED)

    /** 내용 보기 화면으로 열 압축 파일 경로. 화면 호스트가 받아 이동한다. */
    val archiveViewRequests: Flow<String> = _archiveViewRequests.receiveAsFlow()

    /**
     * [item]을 연다.
     * @param item 열 항목
     */
    fun open(item: FileHolderItem) {
        val domain = item.toDomain()
        if (archiveViewUseCase.canView(domain)) _archiveViewRequests.trySend(item.path)
        else scope.launch { openFileUseCase.open(domain) }
    }
}
