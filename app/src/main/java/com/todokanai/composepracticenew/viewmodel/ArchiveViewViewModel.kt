package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * 압축 파일 내용 보기 화면의 목록 읽기·폴더 탐색·비밀번호 질문을 담당하는 ViewModel.
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

    /** 화면 표시 상태. */
    val uiState: StateFlow<ArchiveViewUiState> =
        combine(listing, folderNames, sortModeUseCase.sortBy) { listing, names, sortMode -> uiStateOf(listing, names, sortMode) }
            .flowOn(Dispatchers.Default) // 큰 폴더의 정렬·날짜 포맷을 메인 스레드 밖에서 한다
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveViewUiState(archiveNameOf(), emptyList(), ArchiveViewContent.Loading))

    /** 답을 기다리는 비밀번호 질문과 그 답을 받을 자리. */
    private val pendingPassword = MutableStateFlow<PendingPassword?>(null)

    /** 화면이 띄울 비밀번호 질문. 없으면 null. */
    val passwordRequest: StateFlow<ArchivePasswordRequest?> =
        pendingPassword.map { it?.request }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 질문 id 발급기. 다시 물을 때 다이얼로그를 새로 그려 앞서 입력한 값이 남지 않도록 질문마다 새 id를 준다. */
    private val questionIds = AtomicLong()

    private val _closeRequest = Channel<Unit>(Channel.CONFLATED)

    /** 화면을 닫으라는 요청(비밀번호 질문에서 취소). */
    val closeRequest: Flow<Unit> = _closeRequest.receiveAsFlow()

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
     * 행을 연다 — 폴더면 그 안으로 들어가고, 폴더가 아니면 아무것도 하지 않는다(항목 열기는 지원하지 않는다).
     * @param row 누른 행
     */
    fun openEntry(row: ArchiveEntryRow) {
        if (row.kind != ArchiveEntryRowKind.FOLDER) return
        savedStateHandle[KEY_FOLDER_NAMES] = ArrayList(folderNames.value + row.name)
    }

    /**
     * 뒤로가기를 처리한다. 안쪽 폴더면 바깥 폴더로 가고, 루트면 [close]로 화면을 닫는다.
     * @param close 화면을 닫는 함수
     */
    fun onBackPressed(close: () -> Unit) {
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
        _closeRequest.trySend(Unit)
    }

    /** 목록을 읽는다. 진행 중인 읽기가 있으면 끝내고 새로 읽는다. */
    private fun load() {
        loadJob?.cancel()
        listing.value = ArchiveListing.Loading
        loadJob = viewModelScope.launch {
            listing.value = when (val result = archiveViewUseCase.contentsOf(archivePath, askPassword)) {
                is IoResult.Success -> ArchiveListing.Loaded(result.value).also { keepFolderIn(it.root) }
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
            is ArchiveListing.Loaded -> loadedContentOf(listing.root, names, sortMode)
        }
        return ArchiveViewUiState(archiveNameOf(), names, content)
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
         * @param root 루트 폴더
         */
        data class Loaded(val root: ArchiveFolder) : ArchiveListing

        /**
         * 읽지 못했다.
         * @param failure 실패 결과
         */
        data class Failed(val failure: IoResult.Failure) : ArchiveListing
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
 */
data class ArchiveViewUiState(val archiveName: String, val folderNames: List<String>, val content: ArchiveViewContent)

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
