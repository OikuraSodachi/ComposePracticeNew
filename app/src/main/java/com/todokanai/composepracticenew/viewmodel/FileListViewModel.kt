package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 로컬 파일 목록 화면의 탐색·목록 상태와 로컬 파일 작업(충돌 확인 포함)을 담당하는 ViewModel.
 * @param fileNavigatorUseCase 로컬 디렉터리 탐색 상태를 제공하는 UseCase
 * @param progressUseCase 진행 중인 작업 상태를 제공하는 UseCase
 * @param openFileUseCase 항목 열기(디렉터리 진입 포함)를 수행하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param localFileOperationUseCase 로컬 파일 작업을 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 * @param sorter 정렬 모드 선택 목록을 만드는 FileListSorter
 */
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    progressUseCase: ProgressUseCase,
    private val openFileUseCase: OpenFileUseCase,
    private val sortModeUseCase: SortModeUseCase,
    private val localFileOperationUseCase: LocalFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val messages: OperationMessages,
    sorter: FileListSorter,
) : ViewModel() {

    /**
     * 파일 목록 화면에 필요한 UI 상태를 담는 클래스.
     * @param fileHolderItemList 현재 디렉터리의 파일 목록
     */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(FileHolderItem.fromList(list)) }
        .flowOn(Dispatchers.IO) // from() 항목마다 File().toUri() 문자열화 비용 → 메인 스레드 밖으로 오프로딩
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 동시에 진행 중인 파일 작업들의 progress 상태. 작업 인스턴스 ID(instanceId)를 키로 사용한다. */
    val progressMap: StateFlow<Map<Int, ProgressStateModel>> = progressUseCase.progressMap
        .map { map -> map.mapValues { (_, state) -> state.toModel() } }
        .flowOn(Dispatchers.Default) // 크기 문자열 포맷을 메인 스레드 밖에서 수행
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    /** 현재 정렬 기준. DataStore에서 수집한다. */
    val sortMode: StateFlow<String> = sortModeUseCase.sortBy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Constants.BY_DEFAULT
        )

    /** 정렬 모드 선택 목록과 각 항목 선택 시 실행할 콜백을 반환한다. */
    val sortModeCallbackList = sorter.getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }

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

    /** 현재 경로에 [name] 이름의 새 폴더를 생성한다. */
    fun newFolder(name: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        localFileOperationUseCase.makeDirectory(
            parentPath = currentPath,
            name = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

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
     * [mode] 작업을 현재 경로에 실행한다. 목적지에 같은 이름이 있으면 실행하지 않고 [pendingConflict]로 확인을 요청한다.
     * @param selectedList 작업 대상 항목
     * @param mode 실행할 로컬 작업 모드
     * @return 즉시 실행했으면 true, 사용자 확인 대기로 전환했거나 이미 대기 중이라 거부됐으면 false
     */
    fun requestConfirm(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation): Boolean =
        conflictGate.request(selectedList, conflictsFor(selectedList, mode)) { skip ->
            execute(selectedList, mode, skip)
        }

    /**
     * 대기 중인 충돌을 사용자의 선택대로 처리한다.
     * @param resolution 덮어쓰기·건너뛰기·취소 중 사용자 선택
     * @return 작업을 실행했으면 true
     */
    fun resolveConflict(resolution: ConflictResolution): Boolean = conflictGate.resolve(resolution)

    /** selectedList의 파일들을 [name].zip으로 압축한다. */
    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        val targets = selectedList.map { it.path }.toSet()
        localFileOperationUseCase.zip(
            files = targets,
            zipFilePath = "$parent/$name.zip",
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** [item]의 이름을 [name]으로 변경한다. */
    fun rename(item: FileHolderItem, name: String) {
        localFileOperationUseCase.rename(
            path = item.path,
            newName = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** selectedList의 파일들을 삭제한다. */
    fun delete(selectedList: List<FileHolderItem>) {
        val targets = selectedList.map { it.path }.toSet()
        localFileOperationUseCase.delete(
            files = targets,
            completionMessage = messages.notiDeleteComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** [mode] 작업의 목적지(현재 경로)에 이미 같은 이름으로 존재하는 항목을 반환한다. */
    private fun conflictsFor(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation): List<FileHolderItem> {
        val destinationNames = uiState.value.fileHolderItemList.mapTo(HashSet()) { it.name }
        return when (mode) {
            SelectMode.LocalOperation.Copy, SelectMode.LocalOperation.Move ->
                conflictCheckUseCase.findCopyMoveConflicts(selectedList, destinationNames) { it.name }
            SelectMode.LocalOperation.Unzip, SelectMode.LocalOperation.UnzipHere ->
                conflictCheckUseCase.findUnzipConflicts(selectedList, destinationNames) { it.name }
        }
    }

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 [mode]에 맞는 파일 작업을 현재 경로에 실행한다. */
    private fun execute(selectedList: List<FileHolderItem>, mode: SelectMode.LocalOperation, skipFiles: List<FileHolderItem>) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }.map { it.path }.toSet()
        if (targets.isEmpty()) return
        val onRefresh: suspend () -> Unit = { fileNavigatorUseCase.refresh() }
        when (mode) {
            SelectMode.LocalOperation.Copy -> localFileOperationUseCase.copy(
                files = targets,
                destPath = currentPath,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.Move -> localFileOperationUseCase.move(
                files = targets,
                destPath = currentPath,
                completionMessage = messages.notiMoveComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.Unzip -> localFileOperationUseCase.unzip(
                zipFiles = targets,
                destPath = currentPath,
                unzipHere = false,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            SelectMode.LocalOperation.UnzipHere -> localFileOperationUseCase.unzip(
                zipFiles = targets,
                destPath = currentPath,
                unzipHere = true,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
        }
    }
}
