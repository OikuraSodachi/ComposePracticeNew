package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 로컬 파일 목록 화면의 탐색·목록 상태와 로컬 파일 작업(충돌 확인 포함)을 담당하는 ViewModel.
 * @param fileNavigatorUseCase 로컬 디렉터리 탐색 상태를 제공하는 UseCase
 * @param openFileUseCase 항목 열기(디렉터리 진입 포함)를 수행하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param localFileOperationUseCase 로컬 파일 작업을 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 */
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val openFileUseCase: OpenFileUseCase,
    private val sortModeUseCase: SortModeUseCase,
    private val localFileOperationUseCase: LocalFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val messages: OperationMessages,
) : ViewModel() {

    private companion object {
        /** 로컬 공용 저장소(/storage/emulated, SD 카드)는 대소문자를 구분하지 않으므로 이름 변경 겹침을 대소문자 무시로 비교한다. */
        const val LOCAL_IGNORE_CASE = true
    }

    /**
     * 파일 목록 화면에 필요한 UI 상태를 담는 클래스.
     * @param listing 현재 디렉터리의 목록 상태(조회 중·완료·실패)
     */
    data class UiState(
        val listing: ListingUiState = ListingUiState.NoPath
    )

    val uiState: StateFlow<UiState> = flow {
        // 수집마다 변환기를 새로 만든다 — 변환기는 직전 변환 결과를 기억하므로 하나의 수집 코루틴 안에서만 써야 한다
        val mapper = ListingUiStateMapper(isLocalFile = true)
        emitAll(fileNavigatorUseCase.listing.map { listing -> UiState(mapper.map(listing)) })
    }
        .flowOn(Dispatchers.IO) // 항목마다 File().toUri() 문자열화 비용 → 메인 스레드 밖으로 오프로딩
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 현재 정렬 기준. DataStore에서 수집한다. */
    val sortMode: StateFlow<SortMode> = sortModeUseCase.sortBy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SortMode.BY_DEFAULT
        )

    /** 정렬 다이얼로그에서 선택된 정렬 기준을 저장한다. */
    fun onSortModeSelected(mode: SortMode) {
        sortModeUseCase.saveSortBy(mode)
    }

    /** 경로 breadcrumb에 표시할 디렉터리 트리. */
    val dirTree: StateFlow<List<DirectoryItem>> = fileNavigatorUseCase.dirTree
        .map { list -> list.map { DirectoryItem(name = it.name, path = it.path) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 로컬 작업의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val conflictGate = ConflictGate()
    /** 복사·이동·압축 해제 실행 전 사용자 확인을 기다리는 충돌. */
    val pendingConflict: StateFlow<PendingConflict?> = conflictGate.pending

    /** 이름 변경의 "충돌 판정 → 즉시 실행 / 덮어쓰기 확인 대기" 흐름을 관리한다. */
    private val renameGate = RenameConfirmGate()
    /** 같은 이름의 항목이 있어 덮어쓰기(폴더는 합치기) 확인을 기다리는 이름 변경. */
    val pendingRename: StateFlow<PendingRename?> = renameGate.pending

    /** 새 폴더에 쓸 [name]을 검증해 위반한 규칙 집합을 반환한다. 비어 있으면 유효하다. 실행 시점의 강제는 UseCase가 한다. */
    fun validateEntryName(name: String): Set<FileNameError> = localFileOperationUseCase.validateEntryName(name)

    /**
     * [item]을 [name]으로 바꿀 때의 위반 규칙 집합을 반환한다. 이름 규칙에 더해, 같은 폴더에 종류가 다른 같은 이름 항목이 있으면
     * [FileNameError.EXISTS_AS_OTHER_TYPE]을 포함한다. 같은 종류의 겹침은 오류가 아니라 확인 대상이다.
     * @param item 이름을 바꿀 항목
     * @param name 사용자가 입력한 새 이름
     * @return 위반한 규칙의 집합. 비어 있으면 확인 버튼을 누를 수 있다
     */
    fun validateRename(item: FileHolderItem, name: String): Set<FileNameError> {
        val errors = localFileOperationUseCase.validateEntryName(name)
        val conflict = conflictCheckUseCase.previewRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = fileNavigatorUseCase,
            ignoreCase = LOCAL_IGNORE_CASE,
        )
        return if (conflict == RenameConflict.TYPE_MISMATCH) errors + FileNameError.EXISTS_AS_OTHER_TYPE else errors
    }

    /** 압축 파일 이름([name].zip)을 검증해 위반한 규칙 집합을 반환한다. 실행 시점의 강제는 UseCase가 한다. */
    fun validateZipName(name: String): Set<FileNameError> = localFileOperationUseCase.validateZipName(name)

    /** 현재 경로에 [name] 이름의 새 폴더를 생성한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다. */
    fun newFolder(name: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        localFileOperationUseCase.makeDirectory(
            parentPath = currentPath,
            name = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** 현재 디렉터리의 목록을 다시 조회한다. 목록 조회 실패 화면의 다시 시도에 쓴다. */
    fun retryListing() = fileNavigatorUseCase.refresh()

    /** 주어진 절대 경로로 현재 디렉터리를 이동한다. */
    fun updateCurrentPath(path: String) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(path)
        }
    }

    /** 상위 디렉터리로 이동한다. 루트이면 toStorageFrag를 호출한다. */
    fun onBackPressed(toStorageFrag: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toStorageFrag)
        }
    }

    /** 선택 모드에 따라 항목을 열거나(디렉터리 진입 포함) 무시한다. */
    fun onItemClick(selected: FileHolderItem, selectMode: LocalSelectMode) {
        viewModelScope.launch {
            when (selectMode) {
                SelectMode.Default -> openFileUseCase.open(selected.toDomain())
                SelectMode.MultiSelect -> { }
                is SelectMode.LocalOperation, SelectMode.Download -> if (selected.isDirectory) openFileUseCase.open(selected.toDomain())
            }
        }
    }

    /**
     * [mode] 작업을 현재 경로(충돌을 판정한 확인된 목록의 경로)에 실행한다. 목적지에 같은 이름이 있으면 실행하지 않고 [pendingConflict]로 확인을 요청한다.
     * 목적지 목록을 확인하지 못했으면(조회 중·실패) 충돌을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * @param selectedList 작업 대상 항목
     * @param mode 실행할 로컬 작업 모드
     * @return 즉시 실행했으면 true, 사용자 확인 대기로 전환했거나 이미 대기 중이거나 목적지 목록을 확인하지 못해 거부됐으면 false
     */
    fun requestConfirm(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation): Boolean {
        val checked = conflictsFor(selectedList, mode) ?: return false
        return conflictGate.request(selectedList, checked) { skip ->
            execute(selectedList, mode, skip, checked.destinationPath)
        }
    }

    /**
     * 대기 중인 충돌을 사용자의 선택대로 처리한다.
     * @param resolution 덮어쓰기·건너뛰기·취소 중 사용자 선택
     * @return 작업을 실행했으면 true
     */
    fun resolveConflict(resolution: ConflictResolution): Boolean = conflictGate.resolve(resolution)

    /** selectedList의 파일들을 [name].zip으로 압축한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다. */
    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        val targets = selectedList.map { it.path }.toSet()
        localFileOperationUseCase.zip(
            files = targets,
            parentPath = parent,
            name = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * [item]의 이름을 [name]으로 변경한다. 같은 이름의 파일·폴더가 있으면 실행하지 않고 [pendingRename]으로 덮어쓰기 확인을 요청한다.
     * 폴더 목록을 확인하지 못했으면 겹침을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     */
    fun rename(item: FileHolderItem, name: String) {
        val conflict = renameConflictOf(item, name) ?: return
        renameGate.request(item, name, conflict) { overwrite ->
            localFileOperationUseCase.rename(
                path = item.path,
                newName = name,
                overwrite = overwrite,
                completionMessage = messages.notiComplete,
                onRefresh = { fileNavigatorUseCase.refresh() }
            )
        }
    }

    /** 확인을 기다리는 이름 변경을 덮어쓰기(폴더는 합치기)로 실행한다. */
    fun confirmRename() = renameGate.confirm()

    /** 확인을 기다리는 이름 변경을 취소한다. */
    fun cancelRename() = renameGate.cancel()

    /**
     * [item]을 [name]으로 바꿀 때 현재 폴더의 기존 항목과 겹치는 방식을 판정한다.
     * @return 겹치는 방식. 폴더 목록을 확인하지 못했으면 판정할 수 없으므로 null(거부 알림은 ConflictCheckUseCase가 한다)
     */
    private fun renameConflictOf(item: FileHolderItem, name: String): RenameConflict? =
        conflictCheckUseCase.findRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = fileNavigatorUseCase,
            ignoreCase = LOCAL_IGNORE_CASE,
        )

    /** selectedList의 파일들을 삭제한다. */
    fun delete(selectedList: List<FileHolderItem>) {
        val targets = selectedList.map { it.path }.toSet()
        localFileOperationUseCase.delete(
            files = targets,
            completionMessage = messages.notiDeleteComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * [mode] 작업의 목적지(현재 경로)에 이미 같은 이름으로 존재하는 항목을 반환한다.
     * @return 판정한 목적지 경로와 충돌 항목. 현재 경로의 목록을 확인하지 못했으면 판정할 수 없으므로 null
     */
    private fun conflictsFor(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation): DestinationConflicts<FileHolderItem>? =
        when (mode) {
            SelectMode.LocalOperation.Copy, SelectMode.LocalOperation.Move ->
                conflictCheckUseCase.findCopyMoveConflicts(selectedList, fileNavigatorUseCase) { it.name }
            SelectMode.LocalOperation.Unzip, SelectMode.LocalOperation.UnzipHere ->
                conflictCheckUseCase.findUnzipConflicts(selectedList, fileNavigatorUseCase) { it.name }
        }

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 [mode]에 맞는 파일 작업을 [destPath]에 실행한다. */
    private fun execute(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation, skipFiles: List<FileHolderItem>, destPath: String) {
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }.map { it.path }.toSet()
        if (targets.isEmpty()) return
        val onRefresh: suspend () -> Unit = { fileNavigatorUseCase.refresh() }
        when (mode) {
            SelectMode.LocalOperation.Copy -> localFileOperationUseCase.copy(
                files = targets,
                destPath = destPath,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.Move -> localFileOperationUseCase.move(
                files = targets,
                destPath = destPath,
                completionMessage = messages.notiMoveComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.Unzip -> localFileOperationUseCase.unzip(
                zipFiles = targets,
                destPath = destPath,
                unzipHere = false,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.UnzipHere -> localFileOperationUseCase.unzip(
                zipFiles = targets,
                destPath = destPath,
                unzipHere = true,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
        }
    }
}
