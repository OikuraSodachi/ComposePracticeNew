package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ArchiveEntryCopyRepository

/**
 * 압축 파일 내용 보기에서 고른 항목을 로컬 폴더로 복사(압축 해제)하는 UseCase.
 * @param orchestrator 인스턴스 ID·진행 핸들·질문 통로·실행 스코프를 제공하는 공통 오케스트레이터
 * @param repository 고른 항목만 푸는 ArchiveEntryCopyRepository
 */
class ArchiveCopyUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val repository: ArchiveEntryCopyRepository,
) {

    /**
     * [selection]의 항목을 [destPath]에 푼다. 같은 이름의 파일은 그 항목을 풀려 할 때 사용자에게 묻고, 폴더는 묻지 않고 합친다.
     * 고른 항목에 암호화된 것이 있으면 압축 파일 안에서 비밀번호를 한 번 묻고, 틀리면 다시 묻는다.
     * @param selection 내용 보기에서 고른 항목. 비어 있지 않아야 한다
     * @param destPath 풀 대상 디렉터리의 절대 경로
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun copy(selection: ArchiveEntrySelection, destPath: String, onRefresh: suspend () -> Unit) {
        // 빈 선택은 내용 보기가 복사로 넘기지 않는다 — 여기 닿았다면 구현 오류다
        require(selection.keys.isNotEmpty()) { "고른 항목 없이 압축 파일 항목을 복사할 수 없습니다" }
        orchestrator.launchAsking(FileOperationType.ARCHIVE_COPY, onRefresh) { onProgress, prompts ->
            repository.extractEntries(selection, destPath, resolveConflict = prompts, askPassword = prompts, onProgress = onProgress)
        }
    }
}
