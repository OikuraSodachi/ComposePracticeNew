package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ArchiveAddRepository

/**
 * 로컬 파일·폴더를 기존 압축 파일 안의 폴더에 넣는 UseCase.
 * @param orchestrator 인스턴스 ID·진행 핸들·질문 통로·실행 스코프를 제공하는 공통 오케스트레이터
 * @param repository 압축 파일에 항목을 넣는 ArchiveAddRepository
 */
class ArchiveAddUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val repository: ArchiveAddRepository,
) {

    /**
     * [archivePath]의 압축 파일에 항목을 추가할 수 있는 형식인지 판정한다. 내용 보기가 추가 버튼을 보일지 정하는 데 쓴다.
     * @param archivePath 압축 파일 경로
     * @return 추가할 수 있는 형식의 이름이면 true
     */
    fun canAddTo(archivePath: String): Boolean = repository.canAddTo(archivePath)

    /**
     * [sources]를 [archivePath] 압축 파일 안의 [folderNames] 폴더에 넣는다. 같은 이름의 파일은 그 항목을 넣으려 할 때 사용자에게 묻고, 폴더는 묻지 않고 합친다.
     * 압축 파일에 암호화된 항목이 있으면 비밀번호를 한 번 묻고, 틀리면 다시 묻는다.
     * @param sources 넣을 로컬 파일·폴더 경로. 비어 있지 않아야 한다
     * @param archivePath 항목을 넣을 압축 파일 경로
     * @param folderNames 압축 파일 루트부터 넣을 폴더까지의 폴더 이름들. 비었으면 루트
     * @param onRefresh 작업 완료 후 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun add(sources: Set<String>, archivePath: String, folderNames: List<String>, onRefresh: suspend () -> Unit) {
        // 빈 선택은 선택 상태가 꺼내 주지 않는다 — 여기 닿았다면 구현 오류다
        require(sources.isNotEmpty()) { "넣을 항목 없이 압축 파일에 추가할 수 없습니다" }
        orchestrator.launchAsking(FileOperationType.ARCHIVE_ADD, onRefresh) { onProgress, prompts ->
            repository.addFiles(sources, archivePath, folderNames, resolveConflict = prompts, askPassword = prompts, onProgress = onProgress)
        }
    }
}
