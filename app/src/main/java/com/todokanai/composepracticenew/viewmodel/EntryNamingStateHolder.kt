package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.tools.NewFolderNameDefaults
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.flow.StateFlow

/**
 * 목록 화면의 이름 변경(검증·충돌 판정·덮어쓰기 확인 대기)과 새 폴더 이름 제안을 맡는다. 로컬·원격 목록 ViewModel이 구성(composition)으로 보유한다.
 * 화면이 쓰는 멤버는 ViewModel이 다시 노출하지 않고 public 프로퍼티로 둔다. 이름 변경 실행은 저장소마다 다르므로 ViewModel이 [requestRename]에 넘긴다.
 * @param validateEntryName 이름과 그 이름이 놓일 경로를 받아 화면이 다루는 저장소의 이름 규칙 위반 집합을 반환하는 함수
 * @param renameOverwriteOf 이름을 바꿀 항목이 폴더인지를 받아, 같은 종류 항목 위로 덮어쓰면 화면이 다루는 저장소에서 일어나는 일을 반환하는 함수
 * @param conflictCheckUseCase 같은 이름 항목과의 겹침 판정과 겹치지 않는 이름 제안을 하는 UseCase
 * @param folder 판정할 목록을 가진 화면의 탐색 UseCase
 * @param folderNames 새 폴더 다이얼로그의 기본 이름 제공자
 */
class EntryNamingStateHolder(
    private val validateEntryName: (name: String, location: String) -> Set<FileNameError>,
    private val renameOverwriteOf: (isDirectory: Boolean) -> RenameOverwrite,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val folder: FileNavigatorUseCase,
    private val folderNames: NewFolderNameDefaults,
) {

    /** 이름 변경의 "충돌 판정 → 즉시 실행 / 덮어쓰기 확인 대기" 흐름을 관리한다. */
    private val renameGate = RenameConfirmGate()

    /** 같은 이름의 항목이 있어 확인을 기다리는 이름 변경. */
    val pendingRename: StateFlow<PendingRename?> = renameGate.pending

    /**
     * [item]을 [name]으로 바꿀 때의 위반 규칙 집합을 반환한다. 이름 규칙에 더해, 같은 폴더에 종류가 다른 같은 이름 항목이 있으면
     * [FileNameError.EXISTS_AS_OTHER_TYPE]을 포함한다. 같은 종류의 겹침은 오류가 아니라 확인 대상이다.
     * @param item 이름을 바꿀 항목
     * @param name 사용자가 입력한 새 이름
     * @return 위반한 규칙의 집합. 비어 있으면 확인 버튼을 누를 수 있다
     */
    fun validateRename(item: FileHolderItem, name: String): Set<FileNameError> {
        val errors = validateEntryName(name, item.path)
        val conflict = conflictCheckUseCase.previewRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = folder,
        )
        return if (conflict == RenameConflict.TYPE_MISMATCH) errors + FileNameError.EXISTS_AS_OTHER_TYPE else errors
    }

    /**
     * [item]을 [name]으로 바꾸는 요청을 판정한다. 같은 이름의 파일·폴더가 있으면 실행하지 않고 [pendingRename]으로 확인을 요청하고,
     * 아니면 [execute]를 덮어쓰기 없음(false)으로 즉시 부른다. 덮어쓰기 여부를 어떻게 처리할지는 [execute]를 넘긴 쪽이 정한다. 폴더 목록을 확인하지 못했으면 겹침을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * @param item 이름을 바꿀 항목
     * @param name 새 이름
     * @param execute 덮어쓰기 여부를 받아 이름 변경을 실행하는 함수
     */
    fun requestRename(item: FileHolderItem, name: String, execute: (overwrite: Boolean) -> Unit) {
        val conflict = renameConflictOf(item, name) ?: return
        renameGate.request(item, name, conflict, renameOverwriteOf(item.isDirectory), execute)
    }

    /** 확인을 기다리는 이름 변경을 [requestRename]에 넘긴 실행 함수에 덮어쓰기(true)로 넘겨 실행한다. */
    fun confirmRename() = renameGate.confirm()

    /** 확인을 기다리는 이름 변경을 취소한다. */
    fun cancelRename() = renameGate.cancel()

    /** 새 폴더 다이얼로그에 미리 채울 이름. 현재 폴더에 기본 이름이 있으면 `이름 (n)`으로 겹치지 않게 고른다. */
    fun suggestNewFolderName(): String = conflictCheckUseCase.suggestNewEntryName(
        baseName = folderNames.defaultNewFolderName,
        extension = "",
        folder = folder,
    )

    /**
     * [item]을 [name]으로 바꿀 때 현재 폴더의 기존 항목과 겹치는 방식을 판정한다.
     * @return 겹치는 방식. 폴더 목록을 확인하지 못했으면(조회 중·실패) 판정할 수 없으므로 null(거부 알림은 ConflictCheckUseCase가 한다)
     */
    private fun renameConflictOf(item: FileHolderItem, name: String): RenameConflict? =
        conflictCheckUseCase.findRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = folder,
        )
}
