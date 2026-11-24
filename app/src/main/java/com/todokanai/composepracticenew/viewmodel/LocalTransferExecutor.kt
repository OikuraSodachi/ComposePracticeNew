package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.LocalTransferMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ArchiveCopyUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase

/**
 * 로컬 화면이 받는 전송(다운로드·압축 파일 항목 복사)을 로컬 목적지에 실행한다. 전송 종류마다 맡을 UseCase를 exhaustive `when`으로 고른다.
 * 파일 전송 조율 ViewModel이 구성(composition)으로 보유한다 — 조율 ViewModel은 언제 실행할지(선택 상태)만 정하고, 무엇으로 실행할지는 여기서 정한다.
 * @param remoteFileOperationUseCase 다운로드를 실행하는 UseCase
 * @param archiveCopyUseCase 압축 파일에서 고른 항목을 푸는 UseCase
 * @param refreshLocal 작업이 끝난 뒤 로컬 목록을 새로 조회하는 함수
 */
class LocalTransferExecutor(
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val archiveCopyUseCase: ArchiveCopyUseCase,
    private val refreshLocal: suspend () -> Unit,
) {

    /**
     * [transfer]의 대기 대상을 [destPath]로 보낸다. 같은 이름의 로컬 파일은 작업이 그 항목을 쓰려 할 때 묻는다(전역 충돌 질문).
     * @param transfer 로컬 화면이 받은 전송과 그 대기 대상
     * @param destPath 로컬 목적지 디렉터리 경로
     */
    fun run(transfer: LocalTransferMode, destPath: String) {
        when (transfer) {
            is SelectMode.Download -> remoteFileOperationUseCase.download(
                items = transfer.pending.map { it.toDomain() },
                localDestPath = destPath,
                onRefresh = refreshLocal
            )
            is SelectMode.ArchiveCopy -> archiveCopyUseCase.copy(transfer.selection, destPath, onRefresh = refreshLocal)
        }
    }
}
