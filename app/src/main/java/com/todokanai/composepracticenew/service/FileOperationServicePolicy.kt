package com.todokanai.composepracticenew.service

import com.todokanai.composepracticenew.usecase.ProgressUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 진행 상태에서 파일 작업 보호 서비스가 멈출 때를 파생하는 규칙. [FileOperationForegroundService]가 이 규칙만 따른다.
 * 시작은 작업마다 시작 전에 요청한다([FileOperationProtection]).
 * @param progressUseCase 진행 중인 작업 목록을 제공하는 UseCase
 */
class FileOperationServicePolicy @Inject constructor(
    private val progressUseCase: ProgressUseCase
) {

    /**
     * 진행 중인 작업이 없어질 때까지 기다린다. 이미 없으면 바로 돌아온다.
     * 작업은 보호를 요청하기 전에 진행 상태에 등록되므로, 요청한 작업이 끝나기 전에 멈추지 않는다.
     * @return Unit
     */
    suspend fun awaitStop() {
        progressUseCase.progressMap.first { it.isEmpty() }
    }
}
