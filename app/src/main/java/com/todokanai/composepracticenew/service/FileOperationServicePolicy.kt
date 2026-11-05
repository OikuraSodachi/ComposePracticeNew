package com.todokanai.composepracticenew.service

import com.todokanai.composepracticenew.usecase.ProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 진행 상태에서 파일 작업 보호 서비스의 시작 요청과 멈출 때를 파생하는 규칙. MyApplication(시작)과 [FileOperationForegroundService](멈춤)가 이 규칙만 따른다.
 * @param progressUseCase 진행 중인 작업 목록을 제공하는 UseCase
 */
class FileOperationServicePolicy @Inject constructor(
    private val progressUseCase: ProgressUseCase
) {

    /**
     * 진행 중인 작업이 없다가 생길 때마다 방출한다 — 서비스를 시작해야 할 때.
     * 실행 시간 한도로 서비스가 멈춘 뒤 작업이 남아 있는 동안 생긴 작업에는 방출하지 않는다 — 한도 이후의 보호는 지원 범위 밖이다.
     */
    val startRequests: Flow<Unit> = progressUseCase.progressMap
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .filter { hasActive -> hasActive }
        .map { }

    /**
     * 진행 중인 작업이 없어질 때까지 기다린다. 이미 없으면 바로 돌아온다.
     * @return Unit
     */
    suspend fun awaitStop() {
        progressUseCase.progressMap.first { it.isEmpty() }
    }
}
