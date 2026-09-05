package com.todokanai.composepracticenew.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.todokanai.composepracticenew.compose.activity.MainActivity
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.RemoteFileListFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.DirectoryViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/** 앱 전체 navigation graph를 정의하고 각 destination을 composable에 연결한다. */
@Composable
fun AppNavHost(
    activity: MainActivity,
    mViewModel: MainViewModel
) {
    val navController = rememberNavController()
    // activity-scoped: handleProgressIntent(MainActivity)와 동일 인스턴스를 공유하기 위해 activity를 owner로 지정
    // coordinator는 FILE_BROWSER_GRAPH-scoped: 전송 대기·선택 상태는 그래프 범위로 충분하며, STORAGE 복귀 시 리셋이 의도된 동작
    val viewModel: FileListViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val showRemoteProgressDialog by viewModel.showRemoteProgressDialog.collectAsStateWithLifecycle()

    // 원격 알림 클릭 시 현재 화면이 REMOTE_FILE_LIST가 아니면 자동으로 이동한다
    LaunchedEffect(showRemoteProgressDialog) {
        if (showRemoteProgressDialog && navController.currentDestination?.route != NavDestinations.REMOTE_FILE_LIST) {
            navController.navigate(NavDestinations.REMOTE_FILE_LIST)
        }
    }

    NavHost(navController = navController, startDestination = NavDestinations.STORAGE) {
        composable(NavDestinations.STORAGE) {
            StorageFrag(
                modifier = Modifier,
                activity = activity,
                exitStorageFrag = { navController.navigate(NavDestinations.FILE_LIST) },
                exitToRemoteFileFrag = { navController.navigate(NavDestinations.REMOTE_FILE_LIST) },
                setInitialPath = { viewModel.updateCurrentPath(it) }
            )
        }
        navigation(
            startDestination = NavDestinations.FILE_LIST,
            route = NavDestinations.FILE_BROWSER_GRAPH
        ) {
            composable(NavDestinations.FILE_LIST) {
                val graphEntry = remember {
                    navController.getBackStackEntry(NavDestinations.FILE_BROWSER_GRAPH)
                }
                val coordinator: FileTransferCoordinatorViewModel = hiltViewModel(graphEntry)
                val context = LocalContext.current
                val directoryViewModel: DirectoryViewModel = hiltViewModel()
                val dirUiState by directoryViewModel.uiState.collectAsStateWithLifecycle()
                var showDownloadConflictDialog by remember { mutableStateOf(false) }
                var conflictDownloadFiles by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }

                val localSelectMode by coordinator.localSelectMode.collectAsStateWithLifecycle()
                val localSelectedList by coordinator.localSelectedList.collectAsStateWithLifecycle()
                val downloadPendingList by coordinator.downloadPendingList.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    launch {
                        viewModel.downloadError.collect { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    launch {
                        viewModel.operationError.collect { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    launch {
                        viewModel.operationCompletion.collect { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                BackHandler {
                    mViewModel.onBackPressed { navController.popBackStack() }
                }

                FileListFrag(
                    modifier = Modifier,
                    selectMode = localSelectMode,
                    selectedList = localSelectedList,
                    onSelectModeChange = coordinator::setLocalSelectMode,
                    addToList = coordinator::addToLocalList,
                    removeFromList = coordinator::removeFromLocalList,
                    clearList = coordinator::clearLocalList,
                    onSwitchToRemote = {
                        navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                            popUpTo(NavDestinations.STORAGE) { inclusive = false }
                        }
                    },
                    onConfirmDownload = {
                        val conflicts = viewModel.getDownloadConflicts(downloadPendingList)
                        if (conflicts.isEmpty()) {
                            viewModel.onDownload(downloadPendingList)
                            coordinator.clearDownloadPending()
                        } else {
                            conflictDownloadFiles = conflicts
                            showDownloadConflictDialog = true
                        }
                    },
                    onEnterUploadMode = {
                        coordinator.enterUploadMode()
                        // FILE_LIST를 유지해 back 시 coordinator(uploadPendingList)가 살아남도록 — download 경로와 대칭
                        if (!navController.popBackStack(NavDestinations.REMOTE_FILE_LIST, false)) {
                            navController.navigate(NavDestinations.REMOTE_FILE_LIST)
                        }
                    },
                    navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
                    dirTree = dirUiState.dirTree,
                    onDirClick = { directoryViewModel.updateCurrentPath(it) }
                )

                ProgressSection(
                    progressMap = viewModel.progressMap,
                    forceShowTrigger = viewModel.showProgressDialog,
                    onDialogShown = viewModel::onProgressDialogShown,
                    onCancel = {}
                )

                if (showDownloadConflictDialog) {
                    FileConflictDialog(
                        conflictingFiles = conflictDownloadFiles,
                        totalCount = downloadPendingList.size,
                        onOverwrite = {
                            viewModel.onDownload(downloadPendingList)
                            coordinator.clearDownloadPending()
                            showDownloadConflictDialog = false
                        },
                        onSkip = {
                            viewModel.onDownloadSkipping(downloadPendingList, conflictDownloadFiles)
                            coordinator.clearDownloadPending()
                            showDownloadConflictDialog = false
                        },
                        onCancel = { showDownloadConflictDialog = false }
                    )
                }
            }

            composable(NavDestinations.REMOTE_FILE_LIST) {
                val graphEntry = remember {
                    navController.getBackStackEntry(NavDestinations.FILE_BROWSER_GRAPH)
                }
                val coordinator: FileTransferCoordinatorViewModel = hiltViewModel(graphEntry)
                val remoteViewModel: RemoteFileListViewModel = hiltViewModel()
                val remoteDirTree by remoteViewModel.dirTree.collectAsStateWithLifecycle()
                var showUploadConflictDialog by remember { mutableStateOf(false) }
                var conflictUploadFiles by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }

                val remoteSelectMode by coordinator.remoteSelectMode.collectAsStateWithLifecycle()
                val remoteSelectedList by coordinator.remoteSelectedList.collectAsStateWithLifecycle()
                val uploadPendingList by coordinator.uploadPendingList.collectAsStateWithLifecycle()

                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    launch {
                        viewModel.operationError.collect { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    launch {
                        viewModel.operationCompletion.collect { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                BackHandler {
                    remoteViewModel.onBackPressed { navController.popBackStack() }
                }

                RemoteFileListFrag(
                    modifier = Modifier,
                    selectMode = remoteSelectMode,
                    selectedList = remoteSelectedList,
                    onSelectModeChange = coordinator::setRemoteSelectMode,
                    addToList = coordinator::addToRemoteList,
                    removeFromList = coordinator::removeFromRemoteList,
                    clearList = coordinator::clearRemoteList,
                    onSwitchToLocal = {
                        if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                            navController.navigate(NavDestinations.FILE_LIST) {
                                popUpTo(NavDestinations.STORAGE) { inclusive = false }
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
                    onConfirmUpload = {
                        val conflicts = remoteViewModel.getUploadConflicts(uploadPendingList)
                        if (conflicts.isEmpty()) {
                            uploadPendingList.forEach { remoteViewModel.onUpload(it.path) }
                            coordinator.clearUploadPending()
                        } else {
                            conflictUploadFiles = conflicts
                            showUploadConflictDialog = true
                        }
                    },
                    onConnectionLost = { navController.popBackStack(NavDestinations.STORAGE, false) },
                    navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
                    dirTree = remoteDirTree,
                    onDirClick = { remoteViewModel.navigateToDir(it) },
                    fileListViewModel = viewModel
                )

                if (showUploadConflictDialog) {
                    FileConflictDialog(
                        conflictingFiles = conflictUploadFiles,
                        totalCount = uploadPendingList.size,
                        onOverwrite = {
                            uploadPendingList.forEach { remoteViewModel.onUpload(it.path) }
                            coordinator.clearUploadPending()
                            showUploadConflictDialog = false
                        },
                        onSkip = {
                            remoteViewModel.onUploadSkipping(uploadPendingList, conflictUploadFiles)
                            coordinator.clearUploadPending()
                            showUploadConflictDialog = false
                        },
                        onCancel = { showUploadConflictDialog = false }
                    )
                }

                ProgressSection(
                    progressMap = remoteViewModel.remoteProgressMap,
                    forceShowTrigger = viewModel.showRemoteProgressDialog,
                    onDialogShown = viewModel::onRemoteProgressDialogShown,
                    onCancel = remoteViewModel::onCancelTransfer
                )
            }
        }
    }
}
