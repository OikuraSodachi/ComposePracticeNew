package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.tools.PendingRequest
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 로컬 항목을 연다 — 내용을 볼 수 있는 압축 파일은 내용 보기 화면으로 가라고 요청하고, 그 밖(폴더 포함)은 [OpenFileUseCase]로 연다.
 * 목록 행과 검색 결과가 같은 규칙으로 열도록 두 ViewModel이 생성자로 주입받는다. 범위 없이 주입되어 ViewModel마다 자기 요청을 갖는다.
 * @param openFileUseCase 폴더로 이동하고 파일을 다른 앱으로 여는 UseCase
 * @param archiveViewUseCase 내용 보기로 열 압축 파일인지 판정하는 UseCase
 */
class LocalItemOpener @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val archiveViewUseCase: ArchiveViewUseCase,
) {
    /** [archiveViewRequest]의 원본. 화면 호스트가 처리했다고 알릴 때까지 마지막 하나만 보관한다(#534). */
    private val pendingArchiveView = PendingRequest<String>()

    /** 내용 보기 화면으로 열 압축 파일 경로. 없으면 null. 화면 호스트가 받아 이동한다. */
    val archiveViewRequest: StateFlow<String?> = pendingArchiveView.request

    /**
     * [path] 내용 보기 요청을 처리했다고 알린다.
     * @param path 처리한 압축 파일 경로
     */
    fun onArchiveViewHandled(path: String) = pendingArchiveView.consume(path)

    /**
     * [item]을 연다.
     * @param item 열 항목
     */
    suspend fun open(item: FileHolderItem) {
        val domain = item.toDomain()
        if (archiveViewUseCase.canView(domain)) pendingArchiveView.post(item.path)
        else openFileUseCase.open(domain)
    }

    /**
     * [item]이 내용을 볼 수 있는 압축 파일이면 내용 보기 화면으로 가라고 요청한다. 그 밖의 항목은 아무것도 하지 않는다 —
     * 압축 파일에 추가할 곳을 찾는 중에 다른 파일을 외부 앱으로 열지 않도록.
     * @param item 누른 항목
     */
    fun openArchive(item: FileHolderItem) {
        if (archiveViewUseCase.canView(item.toDomain())) pendingArchiveView.post(item.path)
    }
}
