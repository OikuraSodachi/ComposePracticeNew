package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * 로컬 항목을 연다 — 내용을 볼 수 있는 압축 파일은 내용 보기 화면으로 가라고 요청하고, 그 밖(폴더 포함)은 [OpenFileUseCase]로 연다.
 * 목록 행과 검색 결과가 같은 규칙으로 열도록 두 ViewModel이 생성자로 주입받는다. 범위 없이 주입되어 ViewModel마다 자기 요청 채널을 갖는다.
 * @param openFileUseCase 폴더로 이동하고 파일을 다른 앱으로 여는 UseCase
 * @param archiveViewUseCase 내용 보기로 열 압축 파일인지 판정하는 UseCase
 */
class LocalItemOpener @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val archiveViewUseCase: ArchiveViewUseCase,
) {
    /** 내용 보기 이동 요청을 화면 호스트가 수집할 때까지 마지막 하나만 보관하는 채널. */
    private val _archiveViewRequests = Channel<String>(Channel.CONFLATED)

    /** 내용 보기 화면으로 열 압축 파일 경로. 화면 호스트가 받아 이동한다. */
    val archiveViewRequests: Flow<String> = _archiveViewRequests.receiveAsFlow()

    /**
     * [item]을 연다.
     * @param item 열 항목
     */
    suspend fun open(item: FileHolderItem) {
        val domain = item.toDomain()
        if (archiveViewUseCase.canView(domain)) _archiveViewRequests.trySend(item.path)
        else openFileUseCase.open(domain)
    }
}
