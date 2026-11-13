package com.todokanai.composepracticenew.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.UploadCheckingDialog
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.RemoteFileListFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.compose.listview.LocalTransferActions
import com.todokanai.composepracticenew.compose.listview.SelectionInfoActions
import com.todokanai.composepracticenew.compose.presets.dialog.LocalOpenDialogRegistry
import com.todokanai.composepracticenew.compose.presets.dialog.OpenDialogRegistry
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.localMode
import com.todokanai.composepracticenew.ui.model.remoteMode
import com.todokanai.composepracticenew.viewmodel.ChildCountViewModel
import com.todokanai.composepracticenew.viewmodel.FileInfoViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileSearchViewModel
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
import com.todokanai.composepracticenew.viewmodel.ConflictQuestionViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/**
 * 앱 전체 navigation graph를 정의하고 각 destination을 composable에 연결한다.
 * @param viewModelStoreOwner Activity 스코프 MainViewModel을 얻을 owner — 알림 Intent 처리와 동일 인스턴스를 공유한다
 * @param onExitApp 앱 종료 이벤트 수신 시 호스트(Activity)를 종료하는 콜백
 */
@Composable
fun AppNavHost(
    viewModelStoreOwner: ViewModelStoreOwner,
    onExitApp: () -> Unit
) {
    val navController = rememberNavController()
    // activity-scoped: MainActivity.handleProgressIntent와 동일 인스턴스를 공유하기 위해 Activity를 owner로 지정
    // coordinator는 FILE_BROWSER_GRAPH-scoped: 전송 대기·선택 상태는 그래프 범위로 충분하며, STORAGE 복귀 시 리셋이 의도된 동작
    val mainViewModel: MainViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)
    // activity-scoped: 충돌 질문은 화면 이동과 무관하게 하나만 묻는다
    val conflictQuestionViewModel: ConflictQuestionViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)

    // mainViewModel.exit()이 서비스 중단 후 emit — View는 호스트 종료 콜백 호출만 담당
    LaunchedEffect(Unit) {
        mainViewModel.exitEvent.collect { onExitApp() }
    }

    val context = LocalContext.current
    // Unit 키 — NavHost 생존 동안 1회만 수집. 원격 세션이 끊긴 순간 어느 화면에 있든 알린다
    LaunchedEffect(Unit) {
        mainViewModel.connectionLost.collect {
            Toast.makeText(context, context.getString(R.string.toast_connection_lost), Toast.LENGTH_SHORT).show()
        }
    }

    // 키 없음 — AppNavHost가 살아 있는 동안 하나만 둔다. 설정 실패와 작업 메시지가 한 Snackbar 대기열을 함께 써 한 번에 하나만 보이도록
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    // lifecycleOwner 키 — 소유자가 바뀌면 새 소유자의 수명으로 다시 시작. 설정 저장소의 실패를 어느 화면에 있든 알린다(정렬이 바뀌지 않거나 기본값으로 보이는 이유).
    // 화면이 보이는 동안만 꺼내, 보이지 않는 Activity가 실패를 가져가지 않고 보이지 않는 동안의 실패는 다시 보일 때 알리도록.
    // Toast가 아니라 Snackbar로 알린다 — showSnackbar는 사라질 때까지 돌아오지 않아, 다 보여 준 알림만 지우고 시스템 Toast 한도로 버려지는 알림이 없도록
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainViewModel.showPreferenceFailures { failure ->
                snackbarHostState.showSnackbar(context.getString(preferenceFailureMessage(failure)), duration = SnackbarDuration.Long)
            }
        }
    }

    // lifecycleOwner 키 — 소유자가 바뀌면 새 소유자의 수명으로 다시 시작. 파일 작업의 실패·거부·완료를 어느 화면에 있든(저장소 화면 포함) 알린다.
    // 화면이 보이는 동안만 꺼내, 보이지 않는 동안 끝난 작업의 메시지는 버리지 않고 다시 보일 때 알리도록. Snackbar로 알리는 이유는 위와 같다
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainViewModel.showOperationNotices { notice ->
                snackbarHostState.showSnackbar(notice.message, duration = snackbarDurationOf(notice.kind))
            }
        }
    }

    // Unit 키 — NavHost 생존 동안 1회만 수집. 원격 알림 클릭 시 현재 화면이 REMOTE_FILE_LIST가 아니면 이동한다
    LaunchedEffect(Unit) {
        mainViewModel.remoteProgressNavigation.collect {
            if (navController.currentDestination?.route != NavDestinations.REMOTE_FILE_LIST) {
                navController.navigate(NavDestinations.REMOTE_FILE_LIST)
            }
        }
    }

    // 키 없음 — AppNavHost가 살아 있는 동안 하나만 둔다. 다이얼로그가 등록한 수가 리컴포지션마다 초기화되지 않도록
    val openDialogRegistry = remember { OpenDialogRegistry() }

    CompositionLocalProvider(LocalOpenDialogRegistry provides openDialogRegistry) {
        NavHost(navController = navController, startDestination = NavDestinations.STORAGE) {
            composable(NavDestinations.STORAGE) {
                StorageFrag(
                    modifier = Modifier,
                    exitStorageFrag = { navController.navigate(NavDestinations.FILE_LIST) },
                    exitToRemoteFileFrag = { navController.navigate(NavDestinations.REMOTE_FILE_LIST) },
                    onExit = mainViewModel::exit
                )
            }
            // 그래프 안의 백스택 모양은 들어온 경로마다 다르다(의도된 동작) — 탭 전환은 그래프에 화면 하나만 남기고,
            // 업로드 진입·원격 알림은 FILE_LIST 위에 원격 화면을 쌓으며, 다운로드 진입은 원격 화면을 꺼낸다. 원격 화면의 루트 뒤로가기 결과도 그에 따라 저장소/로컬 목록으로 갈린다
            navigation(
                startDestination = NavDestinations.FILE_LIST,
                route = NavDestinations.FILE_BROWSER_GRAPH
            ) {
                composable(NavDestinations.FILE_LIST) {
                    FileListDestination(navController, mainViewModel)
                }
                composable(NavDestinations.REMOTE_FILE_LIST) {
                    RemoteFileListDestination(navController, mainViewModel)
                }
            }
        }
        // 화면 이동과 무관하게 하나만 둔다. 다이얼로그 등록부 안에 두어, 묻는 동안 진행률 다이얼로그가 질문을 덮지 않고 비켜난다
        ConflictQuestionSection(conflictQuestionViewModel)
    }

    NoticeSnackbarHost(snackbarHostState)

    // 화면 이동과 무관하게 하나만 둔다 — 닫음 상태는 Activity 스코프 mainViewModel이 소유
    ProgressSection(
        shownProgress = mainViewModel.shownProgress,
        openDialogRegistry = openDialogRegistry,
        onDismiss = mainViewModel::dismissProgressDialog,
        onCancel = mainViewModel::cancelOperation
    )
}

/** FILE_BROWSER_GRAPH에 스코프된 FileTransferCoordinatorViewModel을 반환한다. */
@Composable
private fun rememberGraphCoordinator(navController: NavHostController): FileTransferCoordinatorViewModel {
    // 그래프 backStackEntry는 destination 재구성 간 동일하므로 키 없이 1회 조회 — 매 리컴포지션 back stack 탐색 방지
    val graphEntry = remember { navController.getBackStackEntry(NavDestinations.FILE_BROWSER_GRAPH) }
    return hiltViewModel(graphEntry)
}

/** FILE_LIST destination의 UI와 상태를 담당한다. */
@Composable
private fun FileListDestination(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val viewModel: FileListViewModel = hiltViewModel()
    /** 정보 다이얼로그의 표시 상태와 선택 항목의 정보 계산을 맡는다. 화면 회전에도 다이얼로그 내용이 유지되도록 이 destination의 백스택 항목에 스코프된다. */
    val infoViewModel: FileInfoViewModel = hiltViewModel()
    /** 폴더 행의 하위 항목 수를 목록 조회 결과마다 세어 둔다. 화면 회전에도 센 결과가 유지되도록 이 destination의 백스택 항목에 스코프된다. */
    val childCountViewModel: ChildCountViewModel = hiltViewModel()
    /** 파일 검색의 열림 여부·검색어·결과를 맡는다. 검색은 destination이 아니라 이 화면 안의 상태이며, 화면 회전에도 이어지도록 이 destination의 백스택 항목에 스코프된다. */
    val searchViewModel: FileSearchViewModel = hiltViewModel()
    val coordinator = rememberGraphCoordinator(navController)

    // State 객체만 보관하고 선택 목록은 하위에서 읽는다 — 선택 변경 시 이 destination 전체가 리컴포지션되지 않도록
    val localSelection = coordinator.localSelection.collectAsStateWithLifecycle()
    // localSelection을 추적하되 모드가 바뀔 때만 이 destination을 리컴포지션 — 선택 목록만 바뀐 경우는 무시
    val localSelectMode by remember(localSelection) { derivedStateOf { localSelection.value.localMode() } }
    // 선택 요약도 State 객체만 보관 — 값은 다이얼로그 호스트에서만 읽어, 계산 결과가 올 때 이 destination 전체가 리컴포지션되지 않도록
    val selectionSummary = infoViewModel.summary.collectAsStateWithLifecycle()

    /** 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음. */
    // infoViewModel·selectionSummary를 키로 — 리컴포지션마다 새 묶음을 만들어 FileListFrag의 건너뛰기가 깨지지 않도록 이 둘이 같은 동안 유지한다
    val infoActions = remember(infoViewModel, selectionSummary) {
        SelectionInfoActions(
            summary = { selectionSummary.value },
            onShow = infoViewModel::show,
            onDismiss = infoViewModel::dismiss,
        )
    }
    /** 로컬 화면의 전송 콜백 묶음. */
    // coordinator·navController를 키로 — 위와 같은 이유로 이 둘이 같은 동안 유지한다
    val transferActions = remember(coordinator, navController) {
        LocalTransferActions(
            onConfirmDownload = coordinator::confirmDownload,
            onEnterUploadMode = onEnterUploadMode@{
                // 넘길 선택이 없으면 업로드 확인 단계가 아니므로 원격 화면으로 가지 않는다
                if (!coordinator.enterUploadMode()) return@onEnterUploadMode
                // 원격 화면이 백스택에 있으면 그리로 돌아가 중복으로 쌓지 않고, 없으면 FILE_LIST 위에 쌓는다 —
                // 원격 화면에서 뒤로가면 로컬 목록으로 돌아온다(그래프 항목이 남아 coordinator의 업로드 대기 상태도 유지)
                if (!navController.popBackStack(NavDestinations.REMOTE_FILE_LIST, false)) {
                    navController.navigate(NavDestinations.REMOTE_FILE_LIST)
                }
            },
        )
    }

    BackHandler {
        viewModel.onBackPressed()
    }

    FileListFrag(
        modifier = Modifier,
        selectMode = localSelectMode,
        selectedList = { localSelection.value.selected },
        info = infoActions,
        transfer = transferActions,
        onStartMultiSelect = coordinator::startLocalMultiSelect,
        onSelectOperation = coordinator::enterLocalOperation,
        onResetSelection = coordinator::resetLocalSelection,
        onSubmitSelection = coordinator::submitLocalSelection,
        addToList = coordinator::addToLocalList,
        removeFromList = coordinator::removeFromLocalList,
        onRangeSelect = coordinator::setLocalSelectedList,
        childCountsFor = childCountViewModel::countsFor,
        onSwitchToRemote = {
            navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                // 그래프 항목은 남긴다 — 그래프 밖(STORAGE)까지 꺼내면 coordinator가 새로 생겨 업로드/다운로드 모드·대기 목록이 사라진다
                popUpTo(NavDestinations.FILE_BROWSER_GRAPH) { inclusive = false }
            }
        },
        navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
        onExit = mainViewModel::exit,
        viewModel = viewModel,
        searchViewModel = searchViewModel
    )
}

/** REMOTE_FILE_LIST destination의 UI와 상태를 담당한다. */
@Composable
private fun RemoteFileListDestination(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val coordinator = rememberGraphCoordinator(navController)
    val remoteViewModel: RemoteFileListViewModel = hiltViewModel()

    // State 객체만 보관하고 선택 목록은 하위에서 읽는다 — 선택 변경 시 이 destination 전체가 리컴포지션되지 않도록
    val remoteSelection = coordinator.remoteSelection.collectAsStateWithLifecycle()
    // remoteSelection을 추적하되 모드가 바뀔 때만 이 destination을 리컴포지션 — 선택 목록만 바뀐 경우는 무시
    val remoteSelectMode by remember(remoteSelection) { derivedStateOf { remoteSelection.value.remoteMode() } }
    val uploadConflict by coordinator.uploadConflict.collectAsStateWithLifecycle()
    val uploadChecking by coordinator.uploadChecking.collectAsStateWithLifecycle()

    BackHandler {
        remoteViewModel.onBackPressed { navController.popBackStack() }
    }

    RemoteFileListFrag(
        modifier = Modifier,
        selectMode = remoteSelectMode,
        selectedList = { remoteSelection.value.selected },
        onStartMultiSelect = coordinator::startRemoteMultiSelect,
        onResetSelection = coordinator::resetRemoteSelection,
        onSubmitSelection = coordinator::submitRemoteSelection,
        addToList = coordinator::addToRemoteList,
        removeFromList = coordinator::removeFromRemoteList,
        onRangeSelect = coordinator::setRemoteSelectedList,
        onSwitchToLocal = {
            if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                navController.navigate(NavDestinations.FILE_LIST) {
                    // 그래프 항목은 남긴다 — 그래프 밖(STORAGE)까지 꺼내면 coordinator가 새로 생겨 업로드/다운로드 모드·대기 목록이 사라진다
                    popUpTo(NavDestinations.FILE_BROWSER_GRAPH) { inclusive = false }
                }
            }
        },
        onEnterDownloadMode = onEnterDownloadMode@{
            // 넘길 선택이 없으면 다운로드 확인 단계가 아니므로 로컬 화면으로 가지 않는다
            if (!coordinator.enterDownloadMode()) return@onEnterDownloadMode
            if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                navController.navigate(NavDestinations.FILE_LIST) {
                    popUpTo(NavDestinations.REMOTE_FILE_LIST) { inclusive = true }
                }
            }
        },
        onConfirmUpload = coordinator::confirmUpload,
        onConnectionLost = { navController.popBackStack(NavDestinations.STORAGE, false) },
        navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
        onExit = mainViewModel::exit,
        viewModel = remoteViewModel
    )

    UploadCheckingDialog(checking = uploadChecking, onCancel = coordinator::cancelUploadCheck)

    uploadConflict?.let { conflict ->
        FileConflictDialog(
            conflictingFiles = conflict.conflicts,
            collisions = conflict.collisions,
            totalCount = conflict.targets.size,
            onOverwrite = { coordinator.resolveUploadConflict(ConflictResolution.OVERWRITE) },
            onSkip = { coordinator.resolveUploadConflict(ConflictResolution.SKIP) },
            onCancel = { coordinator.resolveUploadConflict(ConflictResolution.CANCEL) }
        )
    }
}

/**
 * 화면 하단에 앱 알림 Snackbar를 띄우는 자리. 화면 위에 겹쳐 두며, 터치는 아래 화면으로 그대로 간다.
 * @param hostState 알림을 하나씩 띄우는 Snackbar 상태
 */
@Composable
private fun NoticeSnackbarHost(hostState: SnackbarHostState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(hostState = hostState)
    }
}

/**
 * 파일 작업 메시지를 보여 줄 시간을 정한다. 실패는 원인과 대상을 읽을 수 있게 길게, 완료는 확인만 하면 되므로 짧게 보인다.
 * @param kind 메시지의 종류
 * @return Snackbar를 보여 줄 시간
 */
private fun snackbarDurationOf(kind: OperationNoticeKind): SnackbarDuration = when (kind) {
    OperationNoticeKind.FAILURE -> SnackbarDuration.Long
    OperationNoticeKind.COMPLETION -> SnackbarDuration.Short
}

/**
 * 설정 저장소의 실패 [failure]를 사용자에게 알릴 문구로 바꾼다.
 * @param failure 일어난 실패
 * @return 알릴 문구의 문자열 리소스 id
 */
@StringRes
private fun preferenceFailureMessage(failure: PreferenceFailure): Int = when (failure) {
    PreferenceFailure.READ -> R.string.toast_preference_read_failed
    PreferenceFailure.SAVE -> R.string.toast_preference_save_failed
    PreferenceFailure.RESET -> R.string.toast_preference_reset
}
