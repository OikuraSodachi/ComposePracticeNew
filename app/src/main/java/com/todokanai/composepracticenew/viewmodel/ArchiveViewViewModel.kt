package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ArchiveContents
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.tools.PendingRequest
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRowKind
import com.todokanai.composepracticenew.ui.model.UiText
import com.todokanai.composepracticenew.ui.model.failureTextOf
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * 압축 파일 내용 보기 화면의 목록 읽기·폴더 탐색·선택(복사 대상 꺼내기 포함)·비밀번호 질문을 담당하는 ViewModel.
 * 목록은 화면이 열릴 때 한 번 읽어 두고, 폴더 이동은 읽은 트리 안에서만 한다. 지금 폴더는 [SavedStateHandle]에 이름으로 남겨 프로세스가 다시 시작돼도 이어진다.
 * 비밀번호 질문은 이 화면의 읽기에 묶여 있어(작업이 아니다) 작업 질문함이 아니라 이 ViewModel이 보관한다. 화면을 나가면 viewModelScope와 함께 읽기와 기다림이 끝난다.
 * @param savedStateHandle 내비게이션 인자(압축 파일 경로)와 지금 폴더를 담는 저장 상태
 * @param archiveViewUseCase 압축 파일의 항목 목록을 읽는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회하는 UseCase — 파일 목록과 같은 정렬을 쓴다
 */
@HiltViewModel
class ArchiveViewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val archiveViewUseCase: ArchiveViewUseCase,
    sortModeUseCase: SortModeUseCase,
) : ViewModel() {

    /** 보고 있는 압축 파일의 경로. */
    private val archivePath: String = checkNotNull(savedStateHandle[ARG_ARCHIVE_PATH]) { "압축 파일 경로 인자가 없다" }

    /** 목록 읽기 상태. */
    private val listing = MutableStateFlow<ArchiveListing>(ArchiveListing.Loading)

    /** 루트부터 들어간 폴더 이름들. Bundle에 담기도록 ArrayList로 저장한다. */
    private val folderNames: StateFlow<ArrayList<String>> = savedStateHandle.getStateFlow(KEY_FOLDER_NAMES, ArrayList())

    /** 선택 상태. 목록을 새로 읽으면 기본 모드로 돌아간다. */
    private val selection = MutableStateFlow<ArchiveSelection>(ArchiveSelection.None)

    /** 지금 폴더의 표시 상태(선택 제외). 선택이 바뀔 때마다 정렬·날짜 포맷을 다시 하지 않도록 선택과 따로 만든다. */
    private val folderState: Flow<ArchiveViewUiState> =
        combine(listing, folderNames, sortModeUseCase.sortBy) { listing, names, sortMode -> uiStateOf(listing, names, sortMode) }
            .flowOn(Dispatchers.Default) // 큰 폴더의 정렬·날짜 포맷을 메인 스레드 밖에서 한다

    /** 화면 표시 상태. */
    val uiState: StateFlow<ArchiveViewUiState> =
        combine(folderState, selection) { state, current -> state.copy(selection = selectionOf(state.content, current)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ArchiveViewUiState(archiveNameOf(), emptyList(), ArchiveViewContent.Loading, ArchiveViewSelection.None)
            )

    /** 답을 기다리는 비밀번호 질문과 그 답을 받을 자리. */
    private val pendingPassword = MutableStateFlow<PendingPassword?>(null)

    /** 화면이 띄울 비밀번호 질문. 없으면 null. */
    val passwordRequest: StateFlow<ArchivePasswordRequest?> =
        pendingPassword.map { it?.request }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 질문 id 발급기. 다시 물을 때 다이얼로그를 새로 그려 앞서 입력한 값이 남지 않도록 질문마다 새 id를 준다. */
    private val questionIds = AtomicLong()

    /** [closeRequest]의 원본. 화면이 처리했다고 알릴 때까지 하나만 보관한다(#534). 연속 요청은 한 번의 닫기로 합쳐진다. */
    private val pendingClose = PendingRequest<Unit>()

    /** 화면을 닫으라는 요청(비밀번호 질문에서 취소)이 있으면 Unit. 없으면 null. */
    val closeRequest: StateFlow<Unit?> = pendingClose.request

    /** 화면 닫기 요청을 처리했다고 알린다. */
    fun onCloseHandled() = pendingClose.consume(Unit)

    /** 진행 중인 목록 읽기. 다시 시도하면 앞의 것을 끝낸다. */
    private var loadJob: Job? = null

    /** 목록 읽기가 비밀번호를 묻는 통로. 답을 받을 때까지 기다리고, 취소하면 읽기를 끝낸다. */
    private val askPassword = ArchivePasswordProvider { archiveName, prompt ->
        val pending = PendingPassword(ArchivePasswordRequest(questionIds.incrementAndGet(), archiveName, prompt), CompletableDeferred())
        pendingPassword.value = pending
        try {
            pending.answer.await() ?: throw CancellationException("비밀번호 질문에서 내용 보기를 취소함")
        } finally {
            pendingPassword.compareAndSet(pending, null)
        }
    }

    init {
        load()
    }

    /** 목록을 다시 읽는다. 읽기 실패 안내의 다시 시도에 쓴다. */
    fun retry() = load()

    /**
     * 누른 행을 처리한다. 다중 선택 중이면 그 행의 선택을 토글하고, 기본 모드면 폴더 안으로 들어간다(폴더가 아니면 아무것도 하지 않는다 — 항목 열기는 지원하지 않는다).
     * 다중 선택 중에는 폴더를 옮기지 않는다 — 선택이 보이지 않는 폴더의 항목을 담지 않도록(파일 목록의 #519와 같은 규칙).
     * @param row 누른 행
     */
    fun onRowClick(row: ArchiveEntryRow) {
        when (val current = selection.value) {
            ArchiveSelection.None -> openFolder(row)
            is ArchiveSelection.MultiSelect -> selection.value = current.toggled(row.key)
        }
    }

    /**
     * [anchor]를 선택한 채로 다중 선택으로 들어간다. 길게 눌러 드래그 선택을 시작할 때 부른다. 이미 다중 선택 중이면 아무것도 하지 않는다.
     * @param anchor 길게 누른 행
     */
    fun startMultiSelect(anchor: ArchiveEntryRow) {
        if (selection.value == ArchiveSelection.None) selection.value = ArchiveSelection.MultiSelect(setOf(anchor.key))
    }

    /**
     * 다중 선택 목록을 [rows]로 바꾼다(드래그 범위 선택, 모두 선택). 다중 선택 중이 아니면 아무것도 하지 않는다 — 비어 있어도 다중 선택은 유지한다.
     * @param rows 새 선택 목록
     */
    fun replaceSelection(rows: List<ArchiveEntryRow>) {
        if (selection.value is ArchiveSelection.MultiSelect) selection.value = ArchiveSelection.MultiSelect(rows.mapTo(HashSet()) { it.key })
    }

    /**
     * 다중 선택을 복사 대상으로 꺼내고, 같은 갱신에서 기본 모드로 되돌린다. 꺼낸 값은 고른 폴더와 목록 식별값을 함께 담아, 압축 파일이 그사이 바뀌었는지 풀 때 가릴 수 있다.
     * @return 고른 항목. 다중 선택이 아니거나 비었거나 목록을 다 읽지 않았으면 상태를 바꾸지 않고 null
     */
    fun submitCopy(): ArchiveEntrySelection? {
        val keys = (selection.value as? ArchiveSelection.MultiSelect)?.keys?.takeIf { it.isNotEmpty() } ?: return null
        val loaded = listing.value as? ArchiveListing.Loaded ?: return null
        selection.value = ArchiveSelection.None
        return ArchiveEntrySelection(archivePath, loaded.contents.listing, folderNames.value.toList(), keys)
    }

    /**
     * 뒤로가기를 처리한다. 다중 선택 중이면 선택을 비우고 기본 모드로 돌아가며, 안쪽 폴더면 바깥 폴더로 가고, 루트면 [close]로 화면을 닫는다.
     * @param close 화면을 닫는 함수
     */
    fun onBackPressed(close: () -> Unit) {
        if (selection.value is ArchiveSelection.MultiSelect) {
            selection.value = ArchiveSelection.None
            return
        }
        val names = folderNames.value
        if (names.isEmpty()) close() else savedStateHandle[KEY_FOLDER_NAMES] = ArrayList(names.dropLast(1))
    }

    /**
     * [id] 질문에 [password]로 답한다. 이미 끝난 질문이면 아무것도 하지 않는다.
     * @param id 답할 질문의 id
     * @param password 입력한 비밀번호
     */
    fun answerPassword(id: Long, password: String) {
        pendingPassword.value?.takeIf { it.request.id == id }?.answer?.complete(password)
    }

    /**
     * [id] 질문을 취소한다. 목록 읽기를 끝내고 화면을 닫는다. 이미 끝난 질문이면 아무것도 하지 않는다.
     * @param id 취소할 질문의 id
     */
    fun cancelPassword(id: Long) {
        val pending = pendingPassword.value?.takeIf { it.request.id == id } ?: return
        pending.answer.complete(null)
        pendingClose.post(Unit)
    }

    /**
     * 폴더 행이면 그 안으로 들어간다.
     * @param row 누른 행
     */
    private fun openFolder(row: ArchiveEntryRow) {
        if (row.kind != ArchiveEntryRowKind.FOLDER) return
        savedStateHandle[KEY_FOLDER_NAMES] = ArrayList(folderNames.value + row.name)
    }

    /**
     * 목록을 읽는다. 진행 중인 읽기가 있으면 끝내고 새로 읽는다.
     * 다중 선택을 끝낸다 — 선택은 읽은 목록의 키로 들고 있어, 새로 읽은 목록에서는 같은 키가 다른 항목을 가리킬 수 있다.
     */
    private fun load() {
        loadJob?.cancel()
        selection.value = ArchiveSelection.None
        listing.value = ArchiveListing.Loading
        loadJob = viewModelScope.launch {
            listing.value = when (val result = archiveViewUseCase.contentsOf(archivePath, askPassword)) {
                is IoResult.Success -> ArchiveListing.Loaded(result.value).also { keepFolderIn(it.contents.root) }
                is IoResult.Failure -> ArchiveListing.Failed(result)
            }
        }
    }

    /**
     * 저장해 둔 지금 폴더가 [root]에 없으면(프로세스가 다시 시작되는 동안 압축 파일이 바뀜) 루트로 돌아간다.
     * @param root 새로 읽은 루트 폴더
     */
    private fun keepFolderIn(root: ArchiveFolder) {
        if (root.folderAt(folderNames.value) == null) savedStateHandle[KEY_FOLDER_NAMES] = ArrayList<String>()
    }

    /**
     * 목록 상태·지금 폴더·정렬 기준으로 화면 상태를 만든다.
     * @param listing 목록 읽기 상태
     * @param names 지금 폴더 이름들
     * @param sortMode 정렬 기준
     * @return 화면 상태
     */
    private fun uiStateOf(listing: ArchiveListing, names: List<String>, sortMode: SortMode): ArchiveViewUiState {
        val content = when (listing) {
            ArchiveListing.Loading -> ArchiveViewContent.Loading
            is ArchiveListing.Failed -> ArchiveViewContent.Failed(failureTextOf(listing.failure, kind = null))
            is ArchiveListing.Loaded -> loadedContentOf(listing.contents.root, names, sortMode)
        }
        return ArchiveViewUiState(archiveNameOf(), names, content, ArchiveViewSelection.None)
    }

    /**
     * 선택 상태를 화면에 보일 선택으로 바꾼다.
     * @param content 지금 폴더의 목록 영역 상태
     * @param selection 선택 상태
     * @return 다중 선택이면 지금 폴더의 행 중 선택된 것들(화면의 행 순서)과 그 키
     */
    private fun selectionOf(content: ArchiveViewContent, selection: ArchiveSelection): ArchiveViewSelection = when (selection) {
        ArchiveSelection.None -> ArchiveViewSelection.None
        is ArchiveSelection.MultiSelect -> {
            val selected = (content as? ArchiveViewContent.Loaded)?.rows.orEmpty().filter { it.key in selection.keys }
            ArchiveViewSelection.MultiSelect(selected, selected.mapTo(HashSet()) { it.key })
        }
    }

    /**
     * 읽은 트리에서 지금 폴더의 행들을 만든다.
     * @param root 루트 폴더
     * @param names 지금 폴더 이름들
     * @param sortMode 정렬 기준
     * @return 지금 폴더의 행들. 폴더를 찾지 못하는 동안([keepFolderIn]이 루트로 돌리기 직전)은 읽는 중
     */
    private fun loadedContentOf(root: ArchiveFolder, names: List<String>, sortMode: SortMode): ArchiveViewContent {
        val folder = root.folderAt(names) ?: return ArchiveViewContent.Loading
        return ArchiveViewContent.Loaded(ArchiveEntryRow.fromList(folder.entries.sortedWith(sortMode.comparator)))
    }

    /** 화면 제목으로 쓰는 압축 파일 이름. 로컬 파일 경로이므로 java.io로 나눈다. */
    private fun archiveNameOf(): String = File(archivePath).name

    /** 목록 읽기 상태. */
    private sealed interface ArchiveListing {
        /** 읽는 중. */
        data object Loading : ArchiveListing

        /**
         * 다 읽었다.
         * @param contents 루트 폴더와 그 목록의 식별값
         */
        data class Loaded(val contents: ArchiveContents) : ArchiveListing

        /**
         * 읽지 못했다.
         * @param failure 실패 결과
         */
        data class Failed(val failure: IoResult.Failure) : ArchiveListing
    }

    /** 선택 상태. 선택은 행이 아니라 키로 든다 — 정렬이 바뀌어 행이 새로 만들어져도 선택이 이어지도록. */
    private sealed interface ArchiveSelection {
        /** 기본 모드 — 선택이 없다. */
        data object None : ArchiveSelection

        /**
         * 다중 선택. 지금 폴더 안의 항목만 담는다(다중 선택 중에는 폴더를 옮기지 않는다).
         * @param keys 선택한 항목의 키. 비어 있어도 다중 선택이다
         */
        data class MultiSelect(val keys: Set<ArchiveEntryKey>) : ArchiveSelection {
            /** [key]의 선택을 뒤집은 선택. */
            fun toggled(key: ArchiveEntryKey): MultiSelect = MultiSelect(if (key in keys) keys - key else keys + key)
        }
    }

    /**
     * 답을 기다리는 비밀번호 질문.
     * @param request 화면에 보일 질문
     * @param answer 입력한 비밀번호를 받을 자리. 취소하면 null
     */
    private class PendingPassword(val request: ArchivePasswordRequest, val answer: CompletableDeferred<String?>)

    companion object {
        /** 압축 파일 경로를 담는 내비게이션 인자 이름. */
        const val ARG_ARCHIVE_PATH = "archivePath"

        /** 지금 폴더 이름들을 저장하는 키. */
        private const val KEY_FOLDER_NAMES = "folderNames"
    }
}

/**
 * 내용 보기 화면의 표시 상태.
 * @param archiveName 압축 파일 이름
 * @param folderNames 루트부터 들어간 폴더 이름들
 * @param content 목록 영역의 상태
 * @param selection 선택 상태
 */
data class ArchiveViewUiState(val archiveName: String, val folderNames: List<String>, val content: ArchiveViewContent, val selection: ArchiveViewSelection)

/** 내용 보기 화면의 선택 상태. */
sealed interface ArchiveViewSelection {
    /** 기본 모드 — 폴더 행을 누르면 들어간다. */
    data object None : ArchiveViewSelection

    /**
     * 다중 선택 — 행을 누르면 선택을 토글한다.
     * @param selected 지금 폴더의 행 중 선택된 것들
     * @param keys 선택된 행의 키 — 화면이 행마다 선택 여부를 찾는 데 쓴다
     */
    data class MultiSelect(val selected: List<ArchiveEntryRow>, val keys: Set<ArchiveEntryKey>) : ArchiveViewSelection
}

/** 내용 보기 화면 목록 영역의 상태. 읽기 실패는 빈 폴더가 아니라 실패로 보인다. */
sealed interface ArchiveViewContent {
    /** 읽는 중. */
    data object Loading : ArchiveViewContent

    /**
     * 읽지 못했다.
     * @param detail 실패 원인 문구. 원인을 모르면 null
     */
    data class Failed(val detail: UiText?) : ArchiveViewContent

    /**
     * 지금 폴더의 항목들.
     * @param rows 정렬한 행 목록
     */
    data class Loaded(val rows: List<ArchiveEntryRow>) : ArchiveViewContent
}

/**
 * 내용 보기 화면이 띄우는 비밀번호 질문.
 * @param id 질문 id
 * @param archiveName 비밀번호를 묻는 압축 파일 이름
 * @param prompt 묻는 이유(처음·틀림·틀렸거나 손상)
 */
data class ArchivePasswordRequest(val id: Long, val archiveName: String, val prompt: ArchivePasswordPrompt)
