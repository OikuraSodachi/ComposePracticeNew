package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 확인한 로컬 복사·이동·압축 해제를 "충돌 판정 → 즉시 실행 / 사용자 확인 대기 후 실행"으로 끝까지 진행한다.
 * 확인을 누르면 작업은 확정이므로, 판정 중에 화면을 떠나도(로컬 화면이 백스택에서 빠져 ViewModel이 정리돼도) 판정을 멈추지 않는다.
 * 확인을 기다리는 충돌은 화면 밖에 남아 로컬 화면이 다시 보일 때 묻는다.
 * 판정 결과의 처리와 대기 상태 변경은 모두 메인 스레드에서 해 [ConflictGate]를 한 줄로 다룬다.
 * @param fileNavigatorUseCase 판정할 목적지(현재 경로)의 확인된 목록과 완료 후 새로고침을 제공하는 로컬 탐색 UseCase
 * @param localFileOperationUseCase 로컬 파일 작업을 실행하고 압축 해제가 만들 이름을 구하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판정하는 UseCase
 * @param notices 다른 작업이 확인을 기다리고 있어 실행하지 않은 요청을 알리는 메시지 저장소
 * @param messages 완료·거부 메시지 제공자
 * @param appScope 화면이 사라진 뒤에도 판정을 끝까지 진행하는 앱 수명 스코프
 */
@Singleton
class LocalOperationConfirmer @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val localFileOperationUseCase: LocalFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val notices: OperationNoticeReporter,
    private val messages: OperationMessages,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /** 로컬 작업의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val conflictGate = ConflictGate()
    /** 복사·이동·압축 해제 실행 전 사용자 확인을 기다리는 충돌. */
    val pendingConflict: StateFlow<PendingConflict?> = conflictGate.pending

    /**
     * 확정한 [mode] 작업을 현재 경로(충돌을 판정한 확인된 목록의 경로)에 실행한다. 목적지에 같은 이름이 있으면 실행하지 않고 [pendingConflict]로 확인을 요청한다.
     * 목적지 목록이나 아카이브를 확인하지 못했으면 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * 이미 다른 작업이 확인을 기다리고 있으면 그 작업을 묻지 않고 바꾸지 않도록 실행하지 않고 알린다.
     * 호출한 쪽을 기다리게 하지 않는다 — 복사·이동은 메인 스레드에서 부르면 판정이 일시 중단 없이 이 호출 안에서 끝나고, 압축 해제는 아카이브를 읽는 동안 뒤에서 진행한다.
     * @param selectedList 작업 대상 항목
     * @param mode 실행할 로컬 작업 모드
     */
    fun confirm(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation) {
        appScope.launch(Dispatchers.Main.immediate) {
            val plan = planOf(mode)
            val checked = plan.findConflicts(selectedList) ?: return@launch
            if (conflictGate.pending.value != null) {
                notices.report(OperationNoticeKind.FAILURE, messages.conflictAwaiting)
                return@launch
            }
            conflictGate.request(selectedList, checked.conflicts) { skip ->
                execute(selectedList, plan, skip, checked.destinationPath)
            }
        }
    }

    /**
     * 대기 중인 충돌을 사용자의 선택대로 처리한다.
     * @param resolution 덮어쓰기·건너뛰기·취소 중 사용자 선택
     */
    fun resolveConflict(resolution: ConflictResolution) {
        conflictGate.resolve(resolution)
    }

    /** [mode] 작업의 충돌 판정과 실행 방법을 돌려준다. 작업별 분기는 여기 한 곳에만 둔다. */
    private fun planOf(mode: SelectMode.LocalOperation): LocalOperationPlan =
        when (mode) {
            SelectMode.LocalOperation.Copy -> LocalOperationPlan(::findCopyMoveConflicts) { targets, destPath, onRefresh ->
                localFileOperationUseCase.copy(
                    files = targets,
                    destPath = destPath,
                    completionMessage = messages.notiComplete,
                    onRefresh = onRefresh
                )
            }
            SelectMode.LocalOperation.Move -> LocalOperationPlan(::findCopyMoveConflicts) { targets, destPath, onRefresh ->
                localFileOperationUseCase.move(
                    files = targets,
                    destPath = destPath,
                    completionMessage = messages.notiMoveComplete,
                    onRefresh = onRefresh
                )
            }
            SelectMode.LocalOperation.Unzip -> unzipPlan(ExtractLayout.IntoArchiveFolder)
            SelectMode.LocalOperation.UnzipHere -> unzipPlan(ExtractLayout.IntoDestination)
        }

    /** [layout]으로 압축 해제할 때의 충돌 판정과 실행 방법을 만든다. 판정과 실행이 같은 배치 방식을 쓰도록 한 값으로 묶는다. */
    private fun unzipPlan(layout: ExtractLayout) =
        LocalOperationPlan({ selectedList -> findUnzipConflicts(selectedList, layout) }) { targets, destPath, onRefresh ->
            localFileOperationUseCase.unzip(
                zipFiles = targets,
                destPath = destPath,
                layout = layout,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
        }

    /**
     * 복사·이동의 목적지(현재 경로)에 이미 같은 이름으로 존재하는 항목을 반환한다.
     * @return 판정한 목적지 경로와 충돌 항목. 현재 경로의 목록을 확인하지 못했으면 판정할 수 없으므로 null
     */
    private fun findCopyMoveConflicts(selectedList: List<FileHolderItem>): DestinationConflicts<FileHolderItem>? =
        conflictCheckUseCase.findCopyMoveConflicts(selectedList, fileNavigatorUseCase) { it.name }

    /**
     * [layout]으로 압축 해제할 때 목적지(현재 경로)에 생길 이름이 이미 있는 아카이브를 반환한다.
     * @return 판정한 목적지 경로와 충돌 항목. 현재 경로의 목록이나 아카이브 내용을 확인하지 못했으면 판정할 수 없으므로 null
     */
    private suspend fun findUnzipConflicts(selectedList: List<FileHolderItem>, layout: ExtractLayout): DestinationConflicts<FileHolderItem>? =
        conflictCheckUseCase.findUnzipConflicts(selectedList, fileNavigatorUseCase, nameOf = { it.name }) { item, destinationPath ->
            localFileOperationUseCase.extractedNames(item.path, destinationPath, layout)
        }

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 [plan]의 파일 작업을 [destPath]에 실행한다. */
    private fun execute(selectedList: List<FileHolderItem>, plan: LocalOperationPlan, skipFiles: List<FileHolderItem>, destPath: String) {
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }.map { it.path }.toSet()
        if (targets.isEmpty()) return
        plan.run(targets, destPath) { fileNavigatorUseCase.refresh() }
    }
}

/**
 * 로컬 작업 하나의 충돌 판정과 실행 방법을 묶은 값.
 * @param findConflicts 선택 항목의 목적지 충돌을 판정하는 함수. 목적지 목록을 확인하지 못했으면 null을 반환한다
 * @param run 대상 경로, 목적지 경로, 완료 후 새로고침 동작을 받아 작업을 시작하는 함수
 */
private class LocalOperationPlan(
    val findConflicts: suspend (List<FileHolderItem>) -> DestinationConflicts<FileHolderItem>?,
    val run: (targets: Set<String>, destPath: String, onRefresh: suspend () -> Unit) -> Unit,
)
