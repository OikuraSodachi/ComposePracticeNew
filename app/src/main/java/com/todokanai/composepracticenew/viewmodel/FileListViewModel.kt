package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.opensOnClick
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
import javax.inject.Inject

/**
 * 로컬 파일 목록 화면의 탐색·목록 상태와 로컬 파일 작업(충돌 확인 포함)을 담당하는 ViewModel.
 * @param fileNavigatorUseCase 로컬 디렉터리 탐색 상태를 제공하는 UseCase
 * @param openFileUseCase 항목 열기(디렉터리 진입 포함)를 수행하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param localFileOperationUseCase 로컬 파일 작업을 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 * @param localOperationConfirmer 확인한 복사·이동·압축 해제를 화면 수명과 무관하게 끝까지 진행하는 확인 흐름
 */
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val openFileUseCase: OpenFileUseCase,
    sortModeUseCase: SortModeUseCase,
    private val localFileOperationUseCase: LocalFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val messages: OperationMessages,
    private val localOperationConfirmer: LocalOperationConfirmer,
) : ViewModel() {

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

    /** 정렬 기준의 노출과 변경. */
    val sortModeStateHolder = SortModeStateHolder(sortModeUseCase, viewModelScope)

    /** 경로 breadcrumb 상태와 그 항목으로의 이동. */
    val dirTreeStateHolder = DirTreeStateHolder(fileNavigatorUseCase, viewModelScope)

    /** 복사·이동·압축 해제 실행 전 사용자 확인을 기다리는 충돌. 이 화면을 떠난 사이 생긴 충돌도 다시 보일 때 묻는다. */
    val pendingConflict: StateFlow<PendingConflict?> = localOperationConfirmer.pendingConflict

    /** 이름 변경의 "충돌 판정 → 즉시 실행 / 덮어쓰기 확인 대기" 흐름을 관리한다. */
    private val renameGate = RenameConfirmGate()
    /** 같은 이름의 항목이 있어 덮어쓰기(폴더는 합치기) 확인을 기다리는 이름 변경. */
    val pendingRename: StateFlow<PendingRename?> = renameGate.pending

    /** 새 폴더를 만들 대상인 현재 폴더. 없으면 만들 곳이 없어 화면이 새 폴더 메뉴를 두지 않는다. */
    val newFolderTarget: StateFlow<String?> = fileNavigatorUseCase.currentPath

    /**
     * [target] 폴더에 만들 새 폴더 이름 [name]을 검증해 위반한 규칙 집합을 반환한다. 비어 있으면 유효하다. 실행 시점의 강제는 UseCase가 한다.
     * [target]은 다이얼로그를 연 때 정한 대상 폴더로, [newFolder]에도 같은 값이 넘어간다.
     */
    fun validateEntryName(target: String, name: String): Set<FileNameError> =
        localFileOperationUseCase.validateEntryName(name, target)

    /** [item]을 압축 해제할 수 있는지 반환한다. 판별은 압축 해제 구현이 지원하는 형식을 따르는 UseCase가 한다. */
    fun canUnzip(item: FileHolderItem): Boolean = localFileOperationUseCase.canUnzip(item.toDomain())

    /**
     * [item]을 [name]으로 바꿀 때의 위반 규칙 집합을 반환한다. 이름 규칙에 더해, 같은 폴더에 종류가 다른 같은 이름 항목이 있으면
     * [FileNameError.EXISTS_AS_OTHER_TYPE]을 포함한다. 같은 종류의 겹침은 오류가 아니라 확인 대상이다.
     * @param item 이름을 바꿀 항목
     * @param name 사용자가 입력한 새 이름
     * @return 위반한 규칙의 집합. 비어 있으면 확인 버튼을 누를 수 있다
     */
    fun validateRename(item: FileHolderItem, name: String): Set<FileNameError> {
        val errors = localFileOperationUseCase.validateEntryName(name, item.path)
        val conflict = conflictCheckUseCase.previewRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = fileNavigatorUseCase,
        )
        return if (conflict == RenameConflict.TYPE_MISMATCH) errors + FileNameError.EXISTS_AS_OTHER_TYPE else errors
    }

    /** 압축 다이얼로그가 만드는 아카이브 형식. 형식을 고르는 화면이 없으므로 ZIP 하나다. */
    private val archiveFormat = ArchiveFormat.ZIP

    /**
     * 압축 파일을 만들 폴더를 정한다. 압축 파일은 첫 항목 옆에 만든다. 다이얼로그를 열 때 한 번 정해 [validateArchiveName]과 [compress]에 함께 넘긴다.
     * @param selectedList 압축할 항목
     * @return 첫 항목의 부모 폴더. 항목이 없거나 부모를 정할 수 없으면 null(화면은 압축 메뉴를 두지 않는다)
     */
    fun archiveFolderOf(selectedList: List<FileHolderItem>): String? =
        selectedList.firstOrNull()?.path?.let(fileNavigatorUseCase::parentOf)

    /**
     * [folder]에 만들 압축 파일 이름([name]+[archiveFormat]의 확장자)을 검증해 위반한 규칙 집합을 반환한다. 실행 시점의 강제는 UseCase가 한다.
     * [folder]는 다이얼로그를 연 때 [archiveFolderOf]로 정한 폴더로, [compress]에도 같은 값이 넘어간다.
     */
    fun validateArchiveName(folder: String, name: String): Set<FileNameError> =
        localFileOperationUseCase.validateArchiveName(name, folder, archiveFormat)

    /** 새 폴더 다이얼로그에 미리 채울 이름. 현재 폴더에 기본 이름이 있으면 `이름 (n)`으로 겹치지 않게 고른다. */
    fun suggestNewFolderName(): String = conflictCheckUseCase.suggestNewEntryName(
        baseName = messages.defaultNewFolderName,
        extension = "",
        folder = fileNavigatorUseCase,
    )

    /**
     * 압축 다이얼로그에 미리 채울 이름(확장자 제외). 같은 이름의 아카이브가 있으면 `이름 (n)`으로 겹치지 않게 고른다.
     * @param selectedList 압축할 항목
     * @return 하나면 그 항목의 이름(파일은 확장자 제외), 여러 개면 항목들이 있는 폴더의 이름
     */
    fun suggestArchiveName(selectedList: List<FileHolderItem>): String {
        val first = selectedList.firstOrNull() ?: return ""
        return conflictCheckUseCase.suggestNameBeside(
            siblingPath = first.path,
            baseName = archiveBaseName(selectedList, first),
            extension = archiveFormat.extension,
            folder = fileNavigatorUseCase,
        )
    }

    /**
     * 압축 파일의 기본 이름을 정한다. 항목들이 있는 폴더가 파일시스템 루트라 이름이 없으면 첫 항목의 이름을 쓴다.
     * @param selectedList 압축할 항목
     * @param first [selectedList]의 첫 항목
     * @return 확장자를 뺀 기본 이름
     */
    private fun archiveBaseName(selectedList: List<FileHolderItem>, first: FileHolderItem): String {
        val folderName = fileNavigatorUseCase.parentOf(first.path)?.let(fileNavigatorUseCase::displayNameOf).orEmpty()
        return if (selectedList.size > 1 && folderName.isNotEmpty()) folderName else nameWithoutExtension(first)
    }

    /**
     * 항목의 이름에서 확장자를 뗀다. 폴더 이름의 점은 확장자가 아니므로 그대로 둔다.
     * @param item 이름을 꺼낼 항목
     * @return 파일은 확장자를 뺀 이름, 폴더는 이름 그대로
     */
    private fun nameWithoutExtension(item: FileHolderItem): String =
        if (item.isDirectory) item.name else item.name.substring(0, FileNameParts.baseNameEnd(item.name))

    /**
     * [target] 폴더에 [name] 이름의 새 폴더를 생성한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param target 다이얼로그를 연 때 정한 대상 폴더 — [validateEntryName]이 판정한 것과 같은 폴더
     * @param name 새 폴더 이름
     */
    fun newFolder(target: String, name: String) {
        localFileOperationUseCase.makeDirectory(
            parentPath = target,
            name = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** 현재 디렉터리의 목록을 다시 조회한다. 목록 조회 실패 화면의 다시 시도에 쓴다. */
    fun retryListing() = fileNavigatorUseCase.refresh()

    /** 상위 디렉터리로 이동한다. 루트이면 toStorageFrag를 호출한다. */
    fun onBackPressed(toStorageFrag: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toStorageFrag)
        }
    }

    /** 선택 모드에 따라 항목을 열거나(디렉터리 진입 포함) 무시한다. 여는지는 [opensOnClick]이 정한다. */
    fun onItemClick(selected: FileHolderItem, selectMode: LocalSelectMode) {
        if (!selectMode.opensOnClick(selected)) return
        viewModelScope.launch { openFileUseCase.open(selected.toDomain()) }
    }

    /**
     * 확인한 [mode] 작업을 확정해 [LocalOperationConfirmer]에 넘긴다. 판정·실행은 이 화면의 수명과 무관하게 끝까지 진행된다.
     * @param selection 로컬 화면의 선택에서 꺼낸 작업 대상
     * @param mode 실행할 로컬 작업 모드
     */
    fun requestConfirm(selection: SubmittedSelection, mode: SelectMode.LocalOperation) = localOperationConfirmer.confirm(selection.items, mode)

    /**
     * 대기 중인 충돌을 사용자의 선택대로 처리한다.
     * @param resolution 덮어쓰기·건너뛰기·취소 중 사용자 선택
     */
    fun resolveConflict(resolution: ConflictResolution) = localOperationConfirmer.resolveConflict(resolution)

    /**
     * selection의 파일들을 [folder]에 [name]+[archiveFormat]의 확장자 이름의 아카이브로 압축한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param selection 로컬 화면의 선택에서 꺼낸 압축할 항목
     * @param folder 다이얼로그를 연 때 [archiveFolderOf]로 정한 폴더 — [validateArchiveName]이 판정한 것과 같은 폴더
     * @param name 압축 파일 이름(확장자 제외)
     */
    fun compress(selection: SubmittedSelection, folder: String, name: String) {
        val targets = selection.items.map { it.path }.toSet()
        localFileOperationUseCase.compress(
            files = targets,
            parentPath = folder,
            name = name,
            format = archiveFormat,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * selection의 항목 하나의 이름을 [name]으로 변경한다. 같은 이름의 파일·폴더가 있으면 실행하지 않고 [pendingRename]으로 덮어쓰기 확인을 요청한다.
     * 폴더 목록을 확인하지 못했으면 겹침을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param selection 로컬 화면의 선택에서 꺼낸 항목. 이름 변경은 하나만 선택했을 때만 열리므로 항목이 정확히 하나다
     * @param name 새 이름
     */
    fun rename(selection: SubmittedSelection, name: String) {
        val item = selection.items.single()
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
        )

    /**
     * selection의 파일들을 삭제한다.
     * @param selection 로컬 화면의 선택에서 꺼낸 삭제할 항목
     */
    fun delete(selection: SubmittedSelection) {
        localFileOperationUseCase.delete(
            files = selection.items.map { it.toDomain() },
            completionMessage = messages.notiDeleteComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

}
