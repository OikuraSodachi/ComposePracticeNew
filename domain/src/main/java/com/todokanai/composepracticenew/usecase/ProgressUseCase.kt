package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 파일 작업의 progress 상태를 조회하는 UseCase.
 * 작업 인스턴스 ID 발급과 상태 갱신은 FileOperationOrchestrator가 단독으로 담당한다. 작업의 실패·완료 메시지는 OperationNoticeUseCase가 알린다.
 * @param repo 진행 상태를 저장하는 ProgressRepository 구현체
 */
class ProgressUseCase(repo: ProgressRepository) {
    /** 현재 진행 중인 모든 파일 작업의 진행 상태를 인스턴스 id 기준으로 저장하는 StateFlow. */
    val progressMap: StateFlow<Map<Int, ProgressState>> = repo.progressMap
}
