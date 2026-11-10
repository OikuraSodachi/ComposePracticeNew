package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileLaunchResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.repository.FileLauncher
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/**
 * 로컬 항목을 연다. 디렉터리는 그 안으로 이동하고, 파일은 그 형식을 여는 다른 앱으로 넘긴다. 열지 못하면 이유를 알린다.
 * 원격 파일은 열지 않는다(먼저 내려받아야 하는 별도 기능이며 지원 범위 밖이다).
 * @param nav 디렉터리 탐색에 사용하는 FileNavigatorRepository 구현체
 * @param launcher 파일을 다른 앱으로 여는 FileLauncher
 * @param notices 열지 못한 이유를 화면에 알리도록 맡기는 OperationNoticeReporter
 */
class OpenFileUseCase(
    private val nav: FileNavigatorRepository,
    private val launcher: FileLauncher,
    private val notices: OperationNoticeReporter
) {
    /**
     * item을 연다.
     * @param item 열 항목
     * @return Unit
     */
    suspend fun open(item: FileHolderItem) {
        if (item.isDirectory) nav.navigate(item.path)
        else failureMessageOf(launcher.launch(item.path), item.name)?.let { notices.report(OperationNoticeKind.FAILURE, it) }
    }

    /**
     * 여는 결과에 대해 알릴 실패 메시지를 만든다.
     * @param result 여는 결과
     * @param name 메시지에 넣을 항목 이름
     * @return 알릴 실패 메시지, 다른 앱으로 넘겼으면 null
     */
    private fun failureMessageOf(result: FileLaunchResult, name: String): String? = when (result) {
        FileLaunchResult.Launched -> null
        FileLaunchResult.UnknownType -> "열기 실패: 형식을 알 수 없는 파일입니다 ($name)"
        FileLaunchResult.NoHandler -> "열기 실패: 이 파일을 열 수 있는 앱이 없습니다 ($name)"
    }
}
