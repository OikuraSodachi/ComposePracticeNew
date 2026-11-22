package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.tools.NewFolderNameDefaults
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
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
import javax.inject.Inject

/**
 * 로컬 파일 목록 화면의 탐색·목록 상태와 로컬 파일 작업을 담당하는 ViewModel.
 * @param fileNavigatorUseCase 로컬 디렉터리 탐색 상태를 제공하는 UseCase
 * @param openFileUseCase 항목 열기(디렉터리 진입 포함)를 수행하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param localFileOperationUseCase 로컬 파일 작업을 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param folderNames 새 폴더 다이얼로그의 기본 이름 제공자
 */
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val openFileUseCase: OpenFileUseCase,
    sortModeUseCase: SortModeUseCase,
    private val localFileOperationUseCase: LocalFileOperationUseCase,
    conflictCheckUseCase: ConflictCheckUseCase,
    folderNames: NewFolderNameDefaults,
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

    /** 이름 변경의 검증·충돌 판정·덮어쓰기 확인 대기와 새 폴더의 대상 폴더·이름 제안·검증. */
    val entryNaming = EntryNamingStateHolder(
        validateEntryName = localFileOperationUseCase::validateEntryName,
        renameOverwriteOf = localFileOperationUseCase::renameOverwriteOf,
        conflictCheckUseCase = conflictCheckUseCase,
        folder = fileNavigatorUseCase,
        folderNames = folderNames,
    )

    /** [items]를 압축 해제 한 번에 풀 수 있는지 반환한다. 판별은 압축 해제 구현이 지원하는 형식·분할 볼륨 묶음을 따르는 UseCase가 한다. */
    fun canUnzip(items: List<FileHolderItem>): Boolean = localFileOperationUseCase.canUnzip(items.map { it.toDomain() })

    /** 압축 파일의 형식·위치·기본 이름 제안·이름 검증. */
    val archiveNaming = ArchiveNaming(
        validateArchiveName = localFileOperationUseCase::validateArchiveName,
        conflictCheckUseCase = conflictCheckUseCase,
        folder = fileNavigatorUseCase,
    )

    /**
     * [target] 폴더에 [name] 이름의 새 폴더를 생성한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param target 다이얼로그를 연 때 정한 대상 폴더 — [EntryNamingStateHolder.validateNewFolderName]이 판정한 것과 같은 폴더
     * @param name 새 폴더 이름
     */
    fun newFolder(target: String, name: String) {
        localFileOperationUseCase.makeDirectory(
            parentPath = target,
            name = name,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** 현재 디렉터리의 목록을 다시 조회한다. 목록 조회 실패 화면의 다시 시도에 쓴다. */
    fun retryListing() = fileNavigatorUseCase.refresh()

    /** 상위 디렉터리로 이동한다. 상위 경로가 없으면(로컬은 저장소 볼륨 루트) 아무것도 하지 않는다 — 뒤로가기로 로컬 목록을 나가지 않는다(#90). */
    fun onBackPressed() {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toRoot = {})
        }
    }

    /** 항목을 연다 — 폴더는 그리로 이동하고, 파일은 외부 앱으로 연다. 열지는 선택 모드 규칙(clickActionOf)이 정한다. */
    fun openItem(selected: FileHolderItem) {
        viewModelScope.launch { openFileUseCase.open(selected.toDomain()) }
    }

    /**
     * 확인한 [mode] 작업을 현재 경로에 실행한다. 같은 이름의 항목은 작업이 그 항목을 쓰려 할 때 묻는다(전역 충돌 질문).
     * 현재 경로가 없으면 실행할 곳이 없으므로 아무것도 하지 않는다.
     * @param selection 로컬 화면의 선택에서 꺼낸 작업 대상
     * @param mode 실행할 로컬 작업 모드
     */
    fun requestConfirm(selection: SubmittedSelection, mode: SelectMode.LocalOperation) {
        val destPath = fileNavigatorUseCase.currentPath.value ?: return
        val targets = selection.items.mapTo(LinkedHashSet()) { it.path }
        val onRefresh: suspend () -> Unit = { fileNavigatorUseCase.refresh() }
        when (mode) {
            SelectMode.LocalOperation.Copy -> localFileOperationUseCase.copy(targets, destPath, onRefresh)
            SelectMode.LocalOperation.Move -> localFileOperationUseCase.move(targets, destPath, onRefresh)
            SelectMode.LocalOperation.Unzip -> localFileOperationUseCase.unzip(targets, destPath, ExtractLayout.IntoArchiveFolder, onRefresh)
            SelectMode.LocalOperation.UnzipHere -> localFileOperationUseCase.unzip(targets, destPath, ExtractLayout.IntoDestination, onRefresh)
        }
    }

    /**
     * selection의 파일들을 [folder]에 [name]+[ArchiveNaming.format]의 확장자 이름의 아카이브로 압축한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param selection 로컬 화면의 선택에서 꺼낸 압축할 항목
     * @param folder 다이얼로그를 연 때 [ArchiveNaming.folderOf]로 정한 폴더 — [ArchiveNaming.validateName]이 판정한 것과 같은 폴더
     * @param name 압축 파일 이름(확장자 제외)
     * @param password 압축 파일에 걸 비밀번호(AES-256). null이면 비밀번호 없이 만든다
     */
    fun compress(selection: SubmittedSelection, folder: String, name: String, password: String?) {
        val targets = selection.items.map { it.path }.toSet()
        localFileOperationUseCase.compress(
            files = targets,
            parentPath = folder,
            name = name,
            format = archiveNaming.format,
            password = password,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * selection의 항목 하나의 이름을 [name]으로 변경한다. 겹침 판정과 덮어쓰기 확인 요청은 [entryNaming]이 한다.
     * 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param selection 로컬 화면의 선택에서 꺼낸 한 항목
     * @param name 새 이름
     */
    fun rename(selection: SubmittedItem, name: String) {
        val item = selection.item
        entryNaming.requestRename(item, name) { overwrite ->
            localFileOperationUseCase.rename(
                path = item.path,
                newName = name,
                overwrite = overwrite,
                onRefresh = { fileNavigatorUseCase.refresh() }
            )
        }
    }

    /**
     * selection의 파일들을 삭제한다.
     * @param selection 로컬 화면의 선택에서 꺼낸 삭제할 항목
     */
    fun delete(selection: SubmittedSelection) {
        localFileOperationUseCase.delete(
            files = selection.items.mapTo(LinkedHashSet()) { it.path },
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

}
