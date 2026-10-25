package com.todokanai.composepracticenew.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.RemoteFileListFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.compose.presets.dialog.LocalOpenDialogRegistry
import com.todokanai.composepracticenew.compose.presets.dialog.OpenDialogRegistry
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.localMode
import com.todokanai.composepracticenew.ui.model.remoteMode
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
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
    }

    // 화면 이동과 무관하게 하나만 둔다 — 닫음 상태는 Activity 스코프 mainViewModel이 소유
    ProgressSection(
        shownProgress = mainViewModel.shownProgress,
        openDialogRegistry = openDialogRegistry,
        onDismiss = mainViewModel::dismissProgressDialog,
        // TODO: 작업 취소 연결 예정
        onCancel = {}
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
    val coordinator = rememberGraphCoordinator(navController)

    // State 객체만 보관하고 선택 목록은 하위에서 읽는다 — 선택 변경 시 이 destination 전체가 리컴포지션되지 않도록
    val localSelection = coordinator.localSelection.collectAsStateWithLifecycle()
    // localSelection을 추적하되 모드가 바뀔 때만 이 destination을 리컴포지션 — 선택 목록만 바뀐 경우는 무시
    val localSelectMode by remember(localSelection) { derivedStateOf { localSelection.value.localMode() } }
    // 선택 요약도 State 객체만 보관 — 값은 정보 다이얼로그가 열렸을 때 그 안에서만 읽는다
    val localSelectionSummary = coordinator.localSelectionSummary.collectAsStateWithLifecycle()
    val downloadConflict by coordinator.downloadConflict.collectAsStateWithLifecycle()

    ToastEffect(mainViewModel.operationError, mainViewModel.operationCompletion)

    BackHandler {
        viewModel.onBackPressed { navController.popBackStack() }
    }

    FileListFrag(
        modifier = Modifier,
        selectMode = localSelectMode,
        selectedList = { localSelection.value.selected },
        selectionSummary = { localSelectionSummary.value },
        onStartMultiSelect = coordinator::startLocalMultiSelect,
        onSelectOperation = coordinator::enterLocalOperation,
        onResetSelection = coordinator::resetLocalSelection,
        addToList = coordinator::addToLocalList,
        removeFromList = coordinator::removeFromLocalList,
        onRangeSelect = coordinator::setLocalSelectedList,
        onSwitchToRemote = {
            navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                // 그래프 항목은 남긴다 — 그래프 밖(STORAGE)까지 꺼내면 coordinator가 새로 생겨 업로드/다운로드 모드·대기 목록이 사라진다
                popUpTo(NavDestinations.FILE_BROWSER_GRAPH) { inclusive = false }
            }
        },
        onConfirmDownload = coordinator::confirmDownload,
        onEnterUploadMode = {
            coordinator.enterUploadMode()
            // 원격 화면이 백스택에 있으면 그리로 돌아가 중복으로 쌓지 않고, 없으면 FILE_LIST 위에 쌓는다 —
            // 원격 화면에서 뒤로가면 로컬 목록으로 돌아온다(그래프 항목이 남아 coordinator의 업로드 대기 상태도 유지)
            if (!navController.popBackStack(NavDestinations.REMOTE_FILE_LIST, false)) {
                navController.navigate(NavDestinations.REMOTE_FILE_LIST)
            }
        },
        navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
        onExit = mainViewModel::exit,
        viewModel = viewModel
    )

    downloadConflict?.let { conflict ->
        FileConflictDialog(
            conflictingFiles = conflict.conflicts,
            totalCount = conflict.targets.size,
            onOverwrite = { coordinator.resolveDownloadConflict(ConflictResolution.OVERWRITE) },
            onSkip = { coordinator.resolveDownloadConflict(ConflictResolution.SKIP) },
            onCancel = { coordinator.resolveDownloadConflict(ConflictResolution.CANCEL) }
        )
    }
}

/** REMOTE_FILE_LIST destination의 UI와 상태를 담당한다. */
@Composable
private fun RemoteFileListDestination(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val coordinator = rememberGraphCoordinator(navController)
    val remoteViewModel: RemoteFileListViewModel = hiltViewModel()
    val remoteDirTree by remoteViewModel.dirTree.collectAsStateWithLifecycle()

    // State 객체만 보관하고 선택 목록은 하위에서 읽는다 — 선택 변경 시 이 destination 전체가 리컴포지션되지 않도록
    val remoteSelection = coordinator.remoteSelection.collectAsStateWithLifecycle()
    // remoteSelection을 추적하되 모드가 바뀔 때만 이 destination을 리컴포지션 — 선택 목록만 바뀐 경우는 무시
    val remoteSelectMode by remember(remoteSelection) { derivedStateOf { remoteSelection.value.remoteMode() } }
    val uploadConflict by coordinator.uploadConflict.collectAsStateWithLifecycle()

    ToastEffect(mainViewModel.operationError, mainViewModel.operationCompletion)

    BackHandler {
        remoteViewModel.onBackPressed { navController.popBackStack() }
    }

    RemoteFileListFrag(
        modifier = Modifier,
        selectMode = remoteSelectMode,
        selectedList = { remoteSelection.value.selected },
        onStartMultiSelect = coordinator::startRemoteMultiSelect,
        onResetSelection = coordinator::resetRemoteSelection,
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
        onEnterDownloadMode = { items ->
            coordinator.enterDownloadMode(items)
            if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                navController.navigate(NavDestinations.FILE_LIST) {
                    popUpTo(NavDestinations.REMOTE_FILE_LIST) { inclusive = true }
                }
            }
        },
        onConfirmUpload = coordinator::confirmUpload,
        onConnectionLost = { navController.popBackStack(NavDestinations.STORAGE, false) },
        navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
        dirTree = remoteDirTree,
        onDirClick = { remoteViewModel.navigateToDir(it) },
        onExit = mainViewModel::exit,
        viewModel = remoteViewModel
    )

    uploadConflict?.let { conflict ->
        FileConflictDialog(
            conflictingFiles = conflict.conflicts,
            totalCount = conflict.targets.size,
            onOverwrite = { coordinator.resolveUploadConflict(ConflictResolution.OVERWRITE) },
            onSkip = { coordinator.resolveUploadConflict(ConflictResolution.SKIP) },
            onCancel = { coordinator.resolveUploadConflict(ConflictResolution.CANCEL) }
        )
    }
}

/** 각 Flow에서 문자열을 수집해 Toast로 표시한다. */
@Composable
private fun ToastEffect(vararg messageFlows: Flow<String>) {
    val context = LocalContext.current
    // Unit 키 — destination 생존 동안 수집을 1회만 시작해 메시지 중복 수집을 방지
    LaunchedEffect(Unit) {
        messageFlows.forEach { flow ->
            launch { flow.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
        }
    }
}
