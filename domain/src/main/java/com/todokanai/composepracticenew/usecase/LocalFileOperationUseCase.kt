package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.launchOperation
import com.todokanai.composepracticenew.repository.FileActionRepository

/**
 * 로컬 파일 작업(복사·이동·삭제·압축·압축해제·이름변경·새폴더)을 조율하는 UseCase.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param fileActionRepo 로컬 파일 변경 작업을 실행하는 FileActionRepository
 */
class LocalFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val fileActionRepo: FileActionRepository
) {
    private fun isSamePath(files: Set<String>, destPath: String): Boolean =
        files.any { srcPath ->
            val srcName = srcPath.substringAfterLast('/')
            "$destPath/$srcName" == srcPath ||
            destPath.startsWith("$srcPath/")
        }

    /**
     * [files]를 [destPath]에 복사한다.
     * @param files 복사할 파일의 절대 경로 집합
     * @param destPath 복사 대상 디렉터리의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun copy(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            orchestrator.emitError("복사 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val handle = orchestrator.progressHandle()
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, ACTION_KEY_COPY, onRefresh)) {
            fileActionRepo.copyAction(files, destPath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_COPY))
                state.progress?.let { orchestrator.notifier.copyProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [files]를 삭제한다.
     * @param files 삭제할 파일의 절대 경로 집합
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun delete(files: Set<String>, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = orchestrator.progressHandle()
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, ACTION_KEY_DELETE, onRefresh)) {
            files.forEach { path ->
                fileActionRepo.deleteFile(path) { state ->
                    handle.set(state.copy(actionKey = ACTION_KEY_DELETE))
                    val idx = state.currentIndex ?: return@deleteFile
                    val total = state.listSize ?: return@deleteFile
                    orchestrator.notifier.deleteProgressNoti(idx, total, handle.instanceId)
                }
            }
        }
    }

    /**
     * [files]를 [destPath]로 이동한다.
     * @param files 이동할 파일의 절대 경로 집합
     * @param destPath 이동 대상 디렉터리의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun move(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            orchestrator.emitError("이동 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val handle = orchestrator.progressHandle()
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, ACTION_KEY_MOVE, onRefresh)) {
            fileActionRepo.moveFile(files, destPath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_MOVE))
                state.progress?.let { orchestrator.notifier.moveProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [files]를 [zipFilePath]로 압축한다.
     * @param files 압축할 파일의 절대 경로 집합
     * @param zipFilePath 생성할 zip 파일의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun zip(files: Set<String>, zipFilePath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = orchestrator.progressHandle()
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, ACTION_KEY_ZIP, onRefresh)) {
            fileActionRepo.zipAction(files, zipFilePath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_ZIP))
                state.progress?.let { orchestrator.notifier.zipProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다.
     * @param zipFiles 압축 해제할 zip 파일의 절대 경로 집합
     * @param destPath 압축 해제 대상 디렉터리의 절대 경로
     * @param unzipHere true이면 [destPath]에 직접 해제, false이면 zip 파일명 하위 폴더 생성 후 해제
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = orchestrator.progressHandle()
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, ACTION_KEY_UNZIP, onRefresh)) {
            fileActionRepo.unzipAction(zipFiles, destPath, unzipHere) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_UNZIP))
                state.progress?.let { orchestrator.notifier.unzipProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [path]의 파일 이름을 [newName]으로 변경한다.
     * @param path 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun rename(path: String, newName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = orchestrator.progressHandle() // progress 보고 없음 — 순간 작업
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, onRefresh = onRefresh)) {
            fileActionRepo.renameFile(path, newName)
        }
    }

    /**
     * [parentPath] 아래에 [name] 이름의 디렉터리를 생성한다.
     * @param parentPath 디렉터리를 생성할 부모 디렉터리의 절대 경로
     * @param name 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectory(parentPath: String, name: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = orchestrator.progressHandle() // progress 보고 없음 — 순간 작업
        orchestrator.appScope.launchOperation(handle, orchestrator.callback(completionMessage, onRefresh = onRefresh)) {
            fileActionRepo.makeDirectory(parentPath, name)
        }
    }
}
