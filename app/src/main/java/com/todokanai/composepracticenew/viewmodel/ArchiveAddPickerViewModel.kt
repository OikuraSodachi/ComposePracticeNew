package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.ArchivePickerNavigator
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 압축 파일 내용 보기 안에서 압축 파일에 넣을 로컬 항목(파일·폴더, 여러 개)을 고르는 화면의 열림·폴더 탐색·선택을 담당하는 ViewModel.
 * 내용 보기 destination의 백스택 항목에 스코프된다 — 고르는 화면은 destination이 아니라 내용 보기 안의 상태라, 내용 보기를 닫으면 함께 사라진다.
 * 탐색은 로컬 목록과 따로 두는 전용 탐색([ArchivePickerNavigator])으로 해, 고르는 동안 아래에 있는 로컬 목록의 경로·목록·선택이 바뀌지 않는다.
 * 열림 여부·지금 폴더·선택은 ViewModel에만 둔다 — 화면 회전에는 이어지고, 프로세스가 종료되면 닫힌 채 내용 보기로 돌아온다(화면 안 다이얼로그 `ScreenDialogState`와 같은 규칙).
 * @param savedStateHandle 내비게이션 인자(압축 파일 경로)를 담는 저장 상태
 * @param navigator 고르는 화면 전용 로컬 탐색 UseCase
 */
@HiltViewModel
class ArchiveAddPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ArchivePickerNavigator private val navigator: FileNavigatorUseCase,
) : ViewModel() {

    /** 항목을 넣을 압축 파일의 경로. 고르는 화면은 이 압축 파일이 있는 폴더에서 시작한다. */
    private val archivePath: String = checkNotNull(savedStateHandle[ArchiveViewViewModel.ARG_ARCHIVE_PATH]) { "압축 파일 경로 인자가 없다" }

    /** 항목을 넣을 압축 파일의 표시 이름. 고르는 화면의 제목에 보인다. */
    val archiveName: String = navigator.displayNameOf(archivePath)

    /** 고른 항목을 넣을 압축 파일 안의 폴더(루트부터의 이름들). 고르는 화면이 열려 있을 때만 있고, 닫혀 있으면 null. */
    private val targetFolder = MutableStateFlow<List<String>?>(null)

    /** 고르는 화면이 열려 있는지. */
    val isOpen: StateFlow<Boolean> = targetFolder
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 선택 상태. 고르는 화면에는 작업·전송 상태가 없다. */
    private val selection = SelectionStateHolder<Nothing, Nothing>()

    /** 지금 선택 상태. */
    val selectionState: StateFlow<SelectionState<Nothing, Nothing>> = selection.state

    /** 지금 폴더의 목록 상태를 화면 표시용으로 바꾼 것. */
    val listing: StateFlow<ListingUiState> = flow {
        // 수집마다 변환기를 새로 만든다 — 변환기는 직전 변환 결과를 기억하므로 하나의 수집 코루틴 안에서만 써야 한다
        val mapper = ListingUiStateMapper(isLocalFile = true)
        emitAll(navigator.listing.map(mapper::map))
    }
        .flowOn(Dispatchers.IO) // 항목마다 File().toUri() 문자열화 비용 → 메인 스레드 밖으로 오프로딩
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListingUiState.NoPath)

    /** 경로 표시줄 상태와 그 항목으로의 이동. */
    val dirTreeStateHolder = DirTreeStateHolder(navigator, viewModelScope)

    init {
        navigator.listing
            .onEach { selection.followListing(it, isLocalFile = true) }
            .launchIn(viewModelScope)
    }

    /** 열기([open]의 시작 폴더 이동)가 끝나지 않았으면 true. 이동을 기다리는 동안 들어온 열기를 거르려고 [targetFolder]와 따로 표시한다. */
    private var openInProgress = false

    /**
     * 고르는 화면을 연다. 압축 파일이 있는 폴더로 이동해 새로 조회하고, 그 폴더를 읽을 수 있을 때만 연다(거부는 탐색 저장소가 알린다).
     * 이미 열려 있거나 여는 중이면 아무것도 하지 않는다 — 연타한 두 번째 열기가 고르던 폴더를 시작 폴더로 되돌리지 않도록.
     * @param target 고른 항목을 넣을 압축 파일 안의 폴더(루트부터의 이름들) — 연 때의 내용 보기 폴더
     */
    fun open(target: List<String>) {
        if (openInProgress || targetFolder.value != null) return
        val start = navigator.parentOf(archivePath) ?: return
        // launch 전에 설정 — 이동을 기다리는 동안의 연속 입력도 위 확인에서 걸러진다(저장소 화면의 launchOpening과 같은 방식, #542)
        openInProgress = true
        viewModelScope.launch {
            try {
                if (navigator.setPath(start) != NavigateResult.Navigated) return@launch
                selection.reset()
                targetFolder.value = target.toList()
            } finally {
                openInProgress = false
            }
        }
    }

    /** 고르는 화면을 닫는다. 선택은 버린다. */
    fun close() {
        selection.reset()
        targetFolder.value = null
    }

    /**
     * 고른 항목과 넣을 폴더를 꺼내고, 같은 호출에서 고르는 화면을 닫는다. 연타한 두 번째 확인은 꺼낼 선택이 없어 아무것도 하지 않는다.
     * @return 고른 항목과 넣을 폴더. 열려 있지 않거나 고른 항목이 없으면 상태를 바꾸지 않고 null
     */
    fun submit(): PickedEntries? {
        val target = targetFolder.value ?: return null
        val submitted = selection.submit() ?: return null
        close()
        return PickedEntries(submitted, target)
    }

    /**
     * 항목을 선택에 넣는다. 기본 모드면 그 항목으로 다중 선택을 시작한다 — 고르는 화면에서는 파일을 누르면 바로 고른다.
     * @param item 고를 항목
     */
    fun select(item: FileHolderItem) {
        if (selection.state.value is SelectionState.MultiSelect) selection.add(item) else selection.startMultiSelect(item)
    }

    /**
     * 항목을 선택에서 뺀다.
     * @param item 뺄 항목
     */
    fun deselect(item: FileHolderItem) = selection.remove(item)

    /**
     * [first]를 선택한 채로 다중 선택을 시작한다. 길게 눌러 드래그 선택을 시작할 때 부른다.
     * @param first 길게 누른 항목
     */
    fun startMultiSelect(first: FileHolderItem) = selection.startMultiSelect(first)

    /**
     * 다중 선택 목록을 [items]로 바꾼다(드래그 범위 선택, 모두 선택).
     * @param items 새 선택 목록
     */
    fun replaceSelection(items: List<FileHolderItem>) = selection.replaceSelection(items)

    /**
     * 폴더 안으로 들어간다.
     * @param folder 들어갈 폴더 항목
     */
    fun openFolder(folder: FileHolderItem) {
        viewModelScope.launch { navigator.setPath(folder.path) }
    }

    /** 지금 폴더를 다시 조회한다. 목록 조회 실패 안내의 다시 시도에 쓴다. */
    fun retryListing() = navigator.refresh()

    /**
     * 뒤로가기를 처리한다. 다중 선택 중이면 선택을 비우고, 아니면 바깥 폴더로 가며, 저장소 볼륨 루트면 고르는 화면을 닫는다.
     * 다중 선택 중에는 폴더를 옮기지 않는다(파일 목록의 #519와 같은 규칙).
     */
    fun onBackPressed() {
        if (selection.state.value is SelectionState.MultiSelect) {
            selection.reset()
            return
        }
        viewModelScope.launch { navigator.navigateBack(toRoot = ::close) }
    }
}

/**
 * 고르는 화면에서 꺼낸, 압축 파일에 넣을 항목과 넣을 폴더.
 * @param selection 고른 로컬 항목. 꺼낼 때 고르는 화면의 선택은 이미 비어 있다
 * @param targetFolder 넣을 압축 파일 안의 폴더(루트부터의 이름들)
 */
class PickedEntries(val selection: SubmittedSelection, val targetFolder: List<String>)
