package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.PreparationProgressReporter
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.map
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.fileexplorer.defaultDisplayName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 고른 원격 파일·디렉터리를 하나의 작업으로 지운다. 디렉터리는 트리를 모아 리프부터 지우고, 진행을 작업 전체 기준으로 보고한다.
 * 실패는 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * @param treeCollector 디렉터리 대상의 원격 트리를 모으는 FtpTreeCollector
 * @param commands 삭제 명령(DELE·RMD)과 대상 밖으로 나가는 이동을 실행하는 FtpCommandRunner
 */
@Singleton
class FtpRemoteDeletion @Inject internal constructor(
    private val treeCollector: FtpTreeCollector,
    private val commands: FtpCommandRunner
) {

    /**
     * [targets]의 원격 파일·디렉터리를 하나의 작업으로 삭제한다.
     * 먼저 디렉터리 대상의 트리를 모두 모은 뒤(찾은 항목 수는 작업 하나의 [PreparationProgressReporter]로 보고), 모은 항목 전체를 기준으로 지운 항목 수를 보고한다 —
     * 대상이 여럿이어도 진행이 작업 전체를 가리키도록(#284).
     * 일부 대상이 실패해도 나머지를 끝까지 지운 뒤 실패를 모아 알리며, 연결이 끊기면 즉시 중단한다. 트리를 모으지 못한 디렉터리는 지우지 않고 실패로 알린다.
     * 취소하면 이미 지운 항목은 그대로 둔다.
     * @param targets 삭제할 원격 파일·디렉터리
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 삭제 결과. 실패한 대상이 있으면 대상 이름 아래 실패를 모은 [IoResult.PartiallyFailed]
     */
    suspend fun delete(targets: List<RemoteDeletionTarget>, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        val preparation = PreparationProgressReporter(onProgress)
        val plans = targets.map { target -> target to deletionPlanOf(target, preparation).also { if (it is IoResult.ConnectionLost) return it } }
        val progress = DeletionProgress(plans.sumOf { (_, plan) -> (plan as? IoResult.Success)?.value?.itemCount ?: 0 }, onProgress)
        return forEachCollectingFailures(plans, nameOf = { (target, _) -> target.name }) { (target, plan) ->
            when (val planned = plan.valueOr { return@forEachCollectingFailures it }) {
                DeletionPlan.File -> deleteFile(target.path, progress)
                is DeletionPlan.Tree -> removeDirectoryRecursive(target.path, planned.items, progress)
            }
        }
    }

    /**
     * [target]을 지우기 전에 지울 항목을 정한다. 디렉터리는 원격 트리를 재귀 수집한다.
     * @param target 삭제할 대상
     * @param preparation 작업 하나의 준비 단계 보고기
     * @return 지울 항목, 트리를 모으지 못했으면 그 실패
     */
    private suspend fun deletionPlanOf(target: RemoteDeletionTarget, preparation: PreparationProgressReporter): IoResult<DeletionPlan> =
        if (target.isDirectory) {
            treeCollector.collectRemoteTree(target.path, preparation).map { DeletionPlan.Tree(it) }
        } else {
            preparation.onEntryFound(target.name)
            IoResult.Success(DeletionPlan.File)
        }

    /**
     * path의 파일을 원격 서버에서 삭제한다. 명령을 보내기 전에 진행을 보고한다 — 여러 파일을 지우는 작업이 파일마다 취소를 확인하도록.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     * @param progress 작업 전체의 삭제 진행
     * @return 명령 결과
     */
    private suspend fun deleteFile(path: String, progress: DeletionProgress): IoResult<Unit> {
        progress.beforeDeleting(defaultDisplayName(path))
        val ftpPath = extractFtpPath(path)
        return commands.run(FtpCommand.DELE) { it.deleteFile(ftpPath) }
    }

    /**
     * path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다.
     * collectRemoteTree로 수집한 트리([allItems])를 reversed()로 리프부터 삭제해 RMD 빈 디렉터리 조건을 충족한다.
     * 일부 하위 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알리며, 이때 path 자체는 지우지 않는다.
     * 실패한 항목을 품은 상위 디렉터리는 비울 수 없으므로 RMD를 보내지도, 실패로 세지도 않는다(GNU rm과 같은 방식).
     * 트리 수집이 세션을 하위 폴더 안에 남기므로, RMD는 세션이 지울 폴더 안에 있으면 먼저 [path]의 상위 폴더로 나간 뒤 보낸다.
     * 알려진 문제(방치하기로 결정): 트리에 서버가 숨긴 항목이 빠질 수 있어([FtpTreeCollector.collectRemoteTree]), 그런 항목이 있으면 보이는 항목을 모두 지운 뒤에야
     * RMD 거부로 실패를 알게 된다. 이 경우 거부 메시지에 숨긴 항목이 남았을 가능성을 덧붙인다([FtpReplyRules.HIDDEN_ENTRIES_NOTE]).
     * 해석하지 못한 목록 줄은 무엇인지 몰라 지우지 못한 항목으로 알리고, 그 줄이 나온 디렉터리는 지우지 않는다(#255).
     * 항목마다 명령을 보내기 전에 진행을 보고한다 — 보고가 취소를 확인하므로 지우는 동안에도 취소가 듣는다.
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @param allItems [path] 아래에서 수집한 트리
     * @param progress 작업 전체의 삭제 진행
     * @return 삭제 결과. 하위 항목 일부가 실패하면 [IoResult.PartiallyFailed]
     */
    private suspend fun removeDirectoryRecursive(path: String, allItems: List<ListedItem>, progress: DeletionProgress): IoResult<Unit> {
        val ftpPath = extractFtpPath(path)
        val outside = parentFtpPath(ftpPath)
        forEachCollectingFailures(
            allItems.reversed(),
            nameOf = { it.failureName(path) },
            isBlockedBy = { item, failed -> item.isDirectoryEntry && isRemoteAncestorOf(item.occupiedPath, failed.occupiedPath) }
        ) { item ->
            progress.beforeDeleting(item.progressName)
            when (item) {
                is ListedItem.Unreadable -> IoResult.ItemFailed(item.failureReason)
                is ListedItem.Entry -> {
                    val entryFtpPath = extractFtpPath(item.entry.path)
                    if (item.isDirectoryEntry) commands.run(FtpCommand.RMD, commands.leavingTarget(entryFtpPath, outside)) { it.removeDirectory(entryFtpPath) }
                    else commands.run(FtpCommand.DELE) { it.deleteFile(entryFtpPath) }
                }
            }
        }.valueOr { return it }
        progress.beforeDeleting(defaultDisplayName(path))
        return commands.run(FtpCommand.RMD, commands.leavingTarget(ftpPath, outside)) { it.removeDirectory(ftpPath) }
    }
}

/**
 * [FtpRemoteDeletion.delete]로 지울 원격 항목 하나.
 * @param path ftp://server/path 형식의 원격 절대 경로
 * @param name 실패를 알릴 때 쓰는 항목 이름
 * @param isDirectory path가 디렉터리인 경우 true
 */
class RemoteDeletionTarget(val path: String, val name: String, val isDirectory: Boolean)

/** 삭제 대상 하나에서 지울 항목. */
private sealed interface DeletionPlan {
    /** 이 대상이 진행에서 차지하는 항목 수. */
    val itemCount: Int

    /** 파일 하나. */
    data object File : DeletionPlan {
        override val itemCount = 1
    }

    /**
     * 디렉터리와 그 아래에서 수집한 트리.
     * @param items 수집한 하위 항목
     */
    class Tree(val items: List<ListedItem>) : DeletionPlan {
        // 마지막에 지우는 디렉터리 자신도 센다
        override val itemCount = items.size + 1
    }
}

/**
 * 삭제 작업 하나의 진행을 지울 항목 전체([total]) 기준으로 보고한다.
 * 명령을 보내기 전에 보고하므로 마지막 명령 직전에도 100%(작업 끝)를 보고하지 않는다 — 진행 다이얼로그는 100%인 작업을 끝난 작업으로 본다.
 * @param total 작업 전체에서 지울 항목 수(디렉터리 대상 자신 포함)
 * @param onProgress 진행 상태를 수신하는 콜백
 */
private class DeletionProgress(private val total: Int, private val onProgress: (ProgressState) -> Unit) {
    /** 이미 명령을 보낸 항목 수. */
    private var processed = 0

    /** [name]을 지우는 명령을 보내기 직전에 진행을 보고한다 — 보고가 취소를 확인하므로 명령마다 취소가 듣는다. */
    fun beforeDeleting(name: String) {
        onProgress(ProgressState(progress = processed * 100 / total, listSize = total, currentIndex = processed, currentFileName = name))
        processed++
    }
}
