package com.todokanai.composepracticenew.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.todokanai.composepracticenew.compose.activity.MainActivity
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.OptionFrag
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.compose.frag.RemoteFileListFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.compose.presets.dialog.ProgressDialog
import com.todokanai.composepracticenew.viewmodel.DirectoryViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
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
    val viewModel: FileListViewModel = hiltViewModel(viewModelStoreOwner = activity)

    var localSelectMode by rememberSaveable { mutableStateOf(Constants.DEFAULT_MODE) }
    val localSelectedList = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })
    ) { mutableStateListOf<FileHolderItem>() }
    var remoteSelectMode by rememberSaveable { mutableStateOf(Constants.DEFAULT_MODE) }
    val remoteSelectedList = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })
    ) { mutableStateListOf<FileHolderItem>() }

    var downloadPendingList by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }
    var uploadPendingList by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }

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
        composable(NavDestinations.FILE_LIST) {
            val directoryViewModel: DirectoryViewModel = hiltViewModel()
            val dirUiState by directoryViewModel.uiState.collectAsStateWithLifecycle()
            val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
            val isProgressActive = progressMap.isNotEmpty()
            var userDismissed by remember { mutableStateOf(false) }
            val showProgressDialogForKey by viewModel.showProgressDialogForKey.collectAsStateWithLifecycle()
            var showDownloadConflictDialog by remember { mutableStateOf(false) }
            var conflictDownloadFiles by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }

            // 새 작업이 시작될 때 dismiss 상태 초기화
            LaunchedEffect(isProgressActive) {
                if (isProgressActive) userDismissed = false
            }

            // 알림 클릭으로 다이얼로그 재표시 요청이 들어올 때 dismiss 상태 초기화
            LaunchedEffect(showProgressDialogForKey) {
                if (showProgressDialogForKey != null) {
                    userDismissed = false
                    viewModel.onProgressDialogShown()
                }
            }

            val activeProgressMap = progressMap.filter { (_, state) -> state.progress < 100 }
            val showProgress = activeProgressMap.isNotEmpty() && !userDismissed

            BackHandler {
                mViewModel.onBackPressed { navController.popBackStack() }
            }

            Column(modifier = Modifier) {
                OptionFrag(
                    modifier = Modifier,
                    activity = activity,
                    navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
                    dirTree = dirUiState.dirTree,
                    onDirClick = { directoryViewModel.updateCurrentPath(it) }
                )
                FileListFrag(
                    modifier = Modifier,
                    selectMode = localSelectMode,
                    selectedList = localSelectedList,
                    onSelectModeChange = { localSelectMode = it },
                    addToList = { localSelectedList.add(it) },
                    removeFromList = { localSelectedList.remove(it) },
                    clearList = { localSelectedList.clear() },
                    onSwitchToRemote = {
                        navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                            popUpTo(NavDestinations.STORAGE) { inclusive = false }
                        }
                    },
                    onConfirmDownload = {
                        val conflicts = viewModel.getDownloadConflicts(downloadPendingList)
                        if (conflicts.isEmpty()) {
                            // stub: 실제 다운로드 로직은 FtpRepository.download() 구현 시 연결
                            downloadPendingList = emptyList()
                        } else {
                            conflictDownloadFiles = conflicts
                            showDownloadConflictDialog = true
                        }
                    },
                    onEnterUploadMode = {
                        uploadPendingList = localSelectedList.toList()
                        remoteSelectMode = Constants.CONFIRM_MODE_UPLOAD
                        localSelectMode = Constants.DEFAULT_MODE
                        localSelectedList.clear()
                        navController.navigate(NavDestinations.REMOTE_FILE_LIST) {
                            popUpTo(NavDestinations.STORAGE) { inclusive = false }
                        }
                    }
                )
            }

            if (showProgress) {
                ProgressDialog(
                    progressMap = activeProgressMap,
                    onDismissRequest = { userDismissed = true }
                )
            }

            if (showDownloadConflictDialog) {
                FileConflictDialog(
                    conflictingFiles = conflictDownloadFiles,
                    totalCount = downloadPendingList.size,
                    onOverwrite = {
                        // stub: 덮어쓰기로 다운로드 진행 — FtpRepository.download() 구현 시 연결
                        downloadPendingList = emptyList()
                        showDownloadConflictDialog = false
                    },
                    onSkip = {
                        // stub: 충돌 제외한 파일만 다운로드 — FtpRepository.download() 구현 시 연결
                        downloadPendingList = emptyList()
                        showDownloadConflictDialog = false
                    },
                    onCancel = { showDownloadConflictDialog = false }
                )
            }

        }
        composable(NavDestinations.REMOTE_FILE_LIST) {
            val remoteViewModel: RemoteFileListViewModel = hiltViewModel()
            val remoteDirTree by remoteViewModel.dirTree.collectAsStateWithLifecycle()
            var showUploadConflictDialog by remember { mutableStateOf(false) }
            var conflictUploadFiles by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }
            BackHandler {
                remoteViewModel.onBackPressed { navController.popBackStack() }
            }

            Column(modifier = Modifier) {
                OptionFrag(
                    modifier = Modifier,
                    activity = activity,
                    navigateToStorage = { navController.popBackStack(NavDestinations.STORAGE, false) },
                    dirTree = remoteDirTree,
                    onDirClick = { remoteViewModel.navigateToDir(it) }
                )
                RemoteFileListFrag(
                    modifier = Modifier,
                    selectMode = remoteSelectMode,
                    selectedList = remoteSelectedList,
                    onSelectModeChange = { remoteSelectMode = it },
                    addToList = { remoteSelectedList.add(it) },
                    removeFromList = { remoteSelectedList.remove(it) },
                    clearList = { remoteSelectedList.clear() },
                    onSwitchToLocal = {
                        if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                            navController.navigate(NavDestinations.FILE_LIST) {
                                popUpTo(NavDestinations.STORAGE) { inclusive = false }
                            }
                        }
                    },
                    onEnterDownloadMode = { items ->
                        downloadPendingList = items
                        localSelectMode = Constants.CONFIRM_MODE_DOWNLOAD
                        remoteSelectMode = Constants.DEFAULT_MODE
                        remoteSelectedList.clear()
                        if (!navController.popBackStack(NavDestinations.FILE_LIST, false)) {
                            navController.navigate(NavDestinations.FILE_LIST) {
                                popUpTo(NavDestinations.STORAGE) { inclusive = false }
                            }
                        }
                    },
                    onConfirmUpload = {
                        val conflicts = remoteViewModel.getUploadConflicts(uploadPendingList)
                        if (conflicts.isEmpty()) {
                            // stub: 실제 업로드 로직은 FtpRepository.upload() 구현 시 연결
                            uploadPendingList = emptyList()
                        } else {
                            conflictUploadFiles = conflicts
                            showUploadConflictDialog = true
                        }
                    }
                )
            }

            if (showUploadConflictDialog) {
                FileConflictDialog(
                    conflictingFiles = conflictUploadFiles,
                    totalCount = uploadPendingList.size,
                    onOverwrite = {
                        // stub: 덮어쓰기로 업로드 진행 — FtpRepository.upload() 구현 시 연결
                        uploadPendingList = emptyList()
                        showUploadConflictDialog = false
                    },
                    onSkip = {
                        // stub: 충돌 제외한 파일만 업로드 — FtpRepository.upload() 구현 시 연결
                        uploadPendingList = emptyList()
                        showUploadConflictDialog = false
                    },
                    onCancel = { showUploadConflictDialog = false }
                )
            }
        }
    }
}
