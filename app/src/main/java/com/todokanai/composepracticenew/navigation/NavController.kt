package com.todokanai.composepracticenew.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.todokanai.composepracticenew.compose.PendingRequestEffect
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.UploadCheckingDialog
import com.todokanai.composepracticenew.compose.frag.ArchiveViewFrag
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.RemoteFileListFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.compose.listview.LocalTransferActions
import com.todokanai.composepracticenew.compose.listview.SelectionInfoActions
import com.todokanai.composepracticenew.compose.presets.dialog.LocalOpenDialogRegistry
import com.todokanai.composepracticenew.compose.presets.dialog.OpenDialogRegistry
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.localMode
import com.todokanai.composepracticenew.ui.model.remoteMode
import com.todokanai.composepracticenew.viewmodel.ArchiveViewViewModel
import com.todokanai.composepracticenew.viewmodel.ChildCountViewModel
import com.todokanai.composepracticenew.viewmodel.FileInfoViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileSearchViewModel
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
import com.todokanai.composepracticenew.viewmodel.OperationQuestionViewModel
import com.todokanai.composepracticenew.viewmodel.AppNoticeViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import com.todokanai.composepracticenew.viewmodel.ProgressDialogViewModel
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/**
 * 앱 전체 navigation graph를 정의하고 각 destination을 composable에 연결한다.
 * @param viewModelStoreOwner Activity 스코프 ViewModel을 얻을 owner — 진행률 다이얼로그는 알림 Intent 처리와 동일 인스턴스를 공유한다
 * @param onExitApp 앱 종료 이벤트 수신 시 호스트(Activity)를 종료하는 콜백
 */
@Composable
fun AppNavHost(
    viewModelStoreOwner: ViewModelStoreOwner,
    onExitApp: () -> Unit
) {
    val navController = rememberNavController()
    // activity-scoped: 시작 때 스토리지 목록을 한 번만 불러오고, 어느 화면에서든 같은 종료 이벤트를 받도록
    val mainViewModel: MainViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)
    // activity-scoped: MainActivity가 알림 클릭으로 띄우라고 요청한 다이얼로그를 같은 인스턴스에서 보이도록 Activity를 owner로 지정
    val progressDialogViewModel: ProgressDialogViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)
    // activity-scoped: 앱 알림은 화면 이동과 무관하게 한곳에서 꺼낸다
    val appNoticeViewModel: AppNoticeViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)
    // activity-scoped: 작업 질문(충돌·비밀번호)은 화면 이동과 무관하게 하나만 묻는다
    val operationQuestionViewModel: OperationQuestionViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner)

    // mainViewModel.exit()이 원격 연결 종료를 요청한 뒤 남긴 종료 요청 — View는 호스트 종료 콜백 호출만 담당
    PendingRequestEffect(mainViewModel.exitRequest, Lifecycle.State.CREATED, { mainViewModel.onExitHandled() }) { onExitApp() }

    // 원격 알림 클릭 시(연결된 원격 세션이 있을 때만 요청) 원격 화면으로 갈 수 있는 화면이면 이동한다. 갈 수 없는 화면이면 요청을 버린다.
    // 알림은 화면 밖 입력이라 RESUMED 대신 지금 route로 판정한다 — 연타해도 첫 이동으로 지금 화면이 원격 화면이 되어 두 번째는 버려진다
    PendingRequestEffect(
        progressDialogViewModel.remoteProgressNavigation,
        Lifecycle.State.CREATED,
        { progressDialogViewModel.onRemoteProgressNavigationHandled() }
    ) {
        if (canNavigateToRemoteFrom(navController.currentDestination?.route)) {
            navController.navigate(NavDestinations.REMOTE_FILE_LIST)
        }
    }

    // 키 없음 — AppNavHost가 살아 있는 동안 하나만 둔다. 다이얼로그가 등록한 수가 리컴포지션마다 초기화되지 않도록
    val openDialogRegistry = remember { OpenDialogRegistry() }

    CompositionLocalProvider(LocalOpenDialogRegistry provides openDialogRegistry) {
        NavHost(navController = navController, startDestination = NavDestinations.STORAGE) {
            composable(NavDestinations.STORAGE) { entry ->
                StorageFrag(
                    modifier = Modifier,
                    // RESUMED를 다시 확인하지 않는다 — StorageFrag가 저장소 클릭을 RESUMED일 때만 받고, 이동 요청도 RESUMED가 된 뒤에 처리하며,
                    // 화면을 떠나면 남은 요청을 버린다(#534·#542)
                    exitStorageFrag = { navController.navigate(NavDestinations.FILE_LIST) },
                    exitToRemoteFileFrag = { navController.navigate(NavDestinations.REMOTE_FILE_LIST) },
                    isCurrentScreen = { navController.currentBackStackEntry == entry },
                    onExit = mainViewModel::exit
                )
            }
            // 그래프 안의 백스택 모양은 들어온 경로마다 다르다(의도된 동작) — 탭 전환은 그래프에 화면 하나만 남기고,
            // 업로드 진입·원격 알림은 FILE_LIST 위에 원격 화면을 쌓으며, 다운로드 진입은 원격 화면을 꺼낸다. 원격 화면의 루트 뒤로가기 결과도 그에 따라 저장소/로컬 목록으로 갈린다
            navigation(
                startDestination = NavDestinations.FILE_LIST,
                route = NavDestinations.FILE_BROWSER_GRAPH
            ) {
                composable(NavDestinations.FILE_LIST) { entry ->
                    FileListDestination(navController, mainViewModel, entry)
                }
                composable(NavDestinations.REMOTE_FILE_LIST) { entry ->
                    RemoteFileListDestination(navController, mainViewModel, entry)
                }
                // 그래프 안에 둔다 — 내용 보기에서 돌아와도 그래프의 로컬 선택·작업 모드가 남도록
                composable(NavDestinations.ARCHIVE_VIEW) { entry ->
                    ArchiveViewDestination(navController, entry)
                }
            }
        }
        // 화면 이동과 무관하게 하나만 둔다. 다이얼로그 등록부 안에 두어, 묻는 동안 진행률 다이얼로그가 질문을 덮지 않고 비켜난다
        OperationQuestionSection(operationQuestionViewModel)
    }

    // 화면 이동과 무관하게 하나만 둔다 — 앱 알림을 어느 화면에 있든(저장소 화면 포함) 알리도록
    AppNoticeHost(appNoticeViewModel::showNotices)

    // 화면 이동과 무관하게 하나만 둔다 — 닫음 상태는 Activity 스코프 progressDialogViewModel이 소유
    ProgressSection(
        shownProgress = progressDialogViewModel.shownProgress,
        openDialogRegistry = openDialogRegistry,
        onDismiss = progressDialogViewModel::dismissProgressDialog,
        onCancel = progressDialogViewModel::cancelOperation
    )
}

/** FILE_BROWSER_GRAPH에 스코프된 FileTransferCoordinatorViewModel을 반환한다. */
@Composable
private fun rememberGraphCoordinator(navController: NavHostController): FileTransferCoordinatorViewModel {
    // 그래프 backStackEntry는 destination 재구성 간 동일하므로 키 없이 1회 조회 — 매 리컴포지션 back stack 탐색 방지
    val graphEntry = remember { navController.getBackStackEntry(NavDestinations.FILE_BROWSER_GRAPH) }
    // FILE_BROWSER_GRAPH-scoped: 전송 대기·선택 상태는 그래프 범위로 충분하며, STORAGE 복귀 시 리셋이 의도된 동작
    return hiltViewModel(graphEntry)
}

/**
 * 원격 진행 알림을 눌렀을 때 [route] 화면에서 원격 화면으로 이동하는지 판정한다.
 * 이미 원격 화면이면 이동하지 않는다. 내용 보기 화면에서도 이동하지 않는다 — 내용 보기 위에 원격 화면을 쌓지 않도록(알림의 진행 다이얼로그는 그대로 뜬다).
 * @param route 지금 화면의 route
 * @return 원격 화면으로 이동하면 true
 */
private fun canNavigateToRemoteFrom(route: String?): Boolean =
    route != NavDestinations.REMOTE_FILE_LIST && route != NavDestinations.ARCHIVE_VIEW

/**
 * [owner]의 화면이 입력을 받는 중인지(RESUMED) 판정한다.
 * 사용자 입력으로 화면을 떠나는 이동(navigate·popBackStack)은 실행하기 직전에 이것으로 확인하고, 아니면 버린다(#535) —
 * `navigate()`·`popBackStack()`을 부르면 떠나는 화면의 back stack entry는 그 안에서 곧바로 RESUMED를 벗어나므로, 연타한 두 번째 이동이 버려진다.
 * 화면 전환 애니메이션 동안에도 entry가 RESUMED가 아니므로 그 사이의 입력도 버려진다(다시 누르면 된다).
 * 전환이 중간에 끊겨도 entry는 STARTED에 남지 않는다 — navigation 2.8.9 NavHost는 전환이 멈추거나 화면이 사라질 때 보이는 entry를 모두 전환 완료로 표시한다(바이트코드로 확인).
 * 사용자 입력이 아닌 상태 변화로 떠나는 이동(세션 없음, 비밀번호 질문 취소로 끝난 내용 보기)은 확인하지 않는다 — 버리면 화면이 그 상태와 맞지 않게 남는다.
 * @param owner 이동을 실행하는 destination의 back stack entry
 * @return RESUMED이면 true
 */
private fun isResumed(owner: LifecycleOwner): Boolean =
    owner.lifecycle.currentState == Lifecycle.State.RESUMED

/** FILE_LIST destination의 UI와 상태를 담당한다. */
@Composable
private fun FileListDestination(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    entry: NavBackStackEntry
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
    /** 하단 원격 전환을 누를 수 있는지. 연결 상태가 바뀔 때만 값이 바뀐다. */
    val remoteSwitchEnabled by coordinator.remoteSwitchEnabled.collectAsStateWithLifecycle()

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
    // coordinator·navController·entry를 키로 — 위와 같은 이유로 이 셋이 같은 동안 유지한다
    val transferActions = remember(coordinator, navController, entry) {
        LocalTransferActions(
            onConfirmDownload = coordinator::confirmDownload,
            onEnterUploadMode = onEnterUploadMode@{
                // 이미 떠나는 중이면 업로드 모드에 다시 들어가지 않고 버린다(#535)
                if (!isResumed(entry)) return@onEnterUploadMode
                // 넘길 선택이 없거나 연결된 원격 세션이 없으면(거부를 알림) 업로드 확인 단계가 아니므로 원격 화면으로 가지 않는다
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

    // 목록 행·검색 결과에서 연 압축 파일을 내용 보기 화면으로 띄운다.
    // 이 화면이 RESUMED가 아니면(이미 다른 화면으로 떠나는 중이면) 요청을 처리한 것으로 하고 버린다 — 연타로 내용 보기가 두 번 쌓이거나,
    // 보관했다가 이 화면으로 돌아오자마자 열리지 않도록(#537). 행 클릭은 폴더 이동·선택도 맡아 클릭 시점에 버릴 수 없으므로 처리 시점에 판정한다
    for (opener in listOf(viewModel.itemOpener, searchViewModel.itemOpener)) {
        PendingRequestEffect(opener.archiveViewRequest, Lifecycle.State.CREATED, opener::onArchiveViewHandled) { path ->
            if (isResumed(entry)) navController.navigate(NavDestinations.archiveView(path))
        }
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
        onSubmitItem = coordinator::submitLocalItem,
        addToList = coordinator::addToLocalList,
        removeFromList = coordinator::removeFromLocalList,
        onReplaceSelection = coordinator::setLocalSelectedList,
        childCountsFor = childCountViewModel::countsFor,
        remoteSwitchEnabled = remoteSwitchEnabled,
        onSwitchToRemote = onSwitchToRemote@{
            // 버튼은 세션이 있을 때만 눌리지만, 비활성이 화면에 반영되기 전에 끊긴 뒤 누른 경우에도 아무것도 하지 않는다(#523) —
            // 세션 없이 원격 화면으로 가면 그 화면이 스스로 닫히며 그래프 밖(STORAGE)까지 나가 로컬 선택·작업 모드가 사라진다
            if (!coordinator.hasRemoteSession()) return@onSwitchToRemote
            // 이미 떠나는 중이면 버린다(#535)
            if (!isResumed(entry)) return@onSwitchToRemote
            navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                // 그래프 항목은 남긴다 — 그래프 밖(STORAGE)까지 꺼내면 coordinator가 새로 생겨 업로드/다운로드 모드·대기 목록이 사라진다
                popUpTo(NavDestinations.FILE_BROWSER_GRAPH) { inclusive = false }
            }
        },
        navigateToStorage = { if (isResumed(entry)) navController.popBackStack(NavDestinations.STORAGE, false) },
        onExit = mainViewModel::exit,
        viewModel = viewModel,
        searchViewModel = searchViewModel
    )
}

/** ARCHIVE_VIEW destination의 UI와 상태를 담당한다. 루트에서 뒤로가거나 비밀번호 질문에서 취소하면 이 화면만 닫는다. */
@Composable
private fun ArchiveViewDestination(navController: NavHostController, entry: NavBackStackEntry) {
    val viewModel: ArchiveViewViewModel = hiltViewModel()

    // 비밀번호 질문에서 취소하면 화면을 닫는다.
    // RESUMED를 확인하지 않는다 — 취소로 목록 읽기가 이미 끝났으므로, 버리면 읽지 못한 내용 보기가 남는다
    PendingRequestEffect(viewModel.closeRequest, Lifecycle.State.CREATED, { viewModel.onCloseHandled() }) {
        navController.popBackStack(NavDestinations.ARCHIVE_VIEW, true)
    }

    BackHandler {
        // 이미 떠나는 중이면 버린다(#535)
        viewModel.onBackPressed { if (isResumed(entry)) navController.popBackStack(NavDestinations.ARCHIVE_VIEW, true) }
    }

    ArchiveViewFrag(modifier = Modifier, viewModel = viewModel)
}

/** REMOTE_FILE_LIST destination의 UI와 상태를 담당한다. */
@Composable
private fun RemoteFileListDestination(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    entry: NavBackStackEntry
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
        // 이미 떠나는 중이면 버린다(#535) — 루트에서 뒤로가기를 연타하면 아래 화면까지 꺼지지 않도록
        remoteViewModel.onBackPressed { if (isResumed(entry)) navController.popBackStack() }
    }

    RemoteFileListFrag(
        modifier = Modifier,
        selectMode = remoteSelectMode,
        selectedList = { remoteSelection.value.selected },
        onStartMultiSelect = coordinator::startRemoteMultiSelect,
        onResetSelection = coordinator::resetRemoteSelection,
        onSubmitSelection = coordinator::submitRemoteSelection,
        onSubmitItem = coordinator::submitRemoteItem,
        addToList = coordinator::addToRemoteList,
        removeFromList = coordinator::removeFromRemoteList,
        onReplaceSelection = coordinator::setRemoteSelectedList,
        onSwitchToLocal = onSwitchToLocal@{
            // 이미 떠나는 중이면 버린다(#535)
            if (!isResumed(entry)) return@onSwitchToLocal
            if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                navController.navigate(NavDestinations.FILE_LIST) {
                    // 그래프 항목은 남긴다 — 그래프 밖(STORAGE)까지 꺼내면 coordinator가 새로 생겨 업로드/다운로드 모드·대기 목록이 사라진다
                    popUpTo(NavDestinations.FILE_BROWSER_GRAPH) { inclusive = false }
                }
            }
        },
        onEnterDownloadMode = onEnterDownloadMode@{
            // 이미 떠나는 중이면 다운로드 모드에 다시 들어가지 않고 버린다(#535)
            if (!isResumed(entry)) return@onEnterDownloadMode
            // 넘길 선택이 없으면 다운로드 확인 단계가 아니므로 로컬 화면으로 가지 않는다
            if (!coordinator.enterDownloadMode()) return@onEnterDownloadMode
            if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                navController.navigate(NavDestinations.FILE_LIST) {
                    popUpTo(NavDestinations.REMOTE_FILE_LIST) { inclusive = true }
                }
            }
        },
        onConfirmUpload = coordinator::confirmUpload,
        // 원격 화면만 닫는다(루트 뒤로가기와 같은 결과) — 저장소까지 거슬러 나가면 아래에 쌓인 로컬 목록과 그래프의 로컬 선택까지 사라진다(#492).
        // 혼자 있던 원격 화면이면 남은 그래프 항목도 함께 꺼져 저장소로 간다.
        // RESUMED를 확인하지 않는다 — 사용자 입력이 아니라 세션이 없어진 결과이므로, 버리면 세션 없는 원격 화면이 남는다
        onNoSession = { navController.popBackStack(NavDestinations.REMOTE_FILE_LIST, true) },
        navigateToStorage = { if (isResumed(entry)) navController.popBackStack(NavDestinations.STORAGE, false) },
        onExit = mainViewModel::exit,
        viewModel = remoteViewModel
    )

    UploadCheckingDialog(checking = uploadChecking, onCancel = coordinator::cancelUploadCheck)

    uploadConflict?.let { conflict ->
        FileConflictDialog(
            conflictingFiles = conflict.conflicts,
            collisions = conflict.collisions,
            canSkip = conflict.canSkip,
            onOverwrite = { coordinator.resolveUploadConflict(ConflictResolution.OVERWRITE) },
            onSkip = { coordinator.resolveUploadConflict(ConflictResolution.SKIP) },
            onCancel = { coordinator.resolveUploadConflict(ConflictResolution.CANCEL) }
        )
    }
}
