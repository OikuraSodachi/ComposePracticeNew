package com.todokanai.composepracticenew.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.todokanai.composepracticenew.compose.activity.MainActivity
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.OptionFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.compose.presets.dialog.ProgressDialog
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import kotlinx.coroutines.flow.map

/** 앱 전체 navigation graph를 정의하고 각 destination을 composable에 연결한다. */
@Composable
fun AppNavHost(
    activity: MainActivity,
    viewModel: FileListViewModel,
    mViewModel: MainViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = NavDestinations.STORAGE) {
        composable(NavDestinations.STORAGE) {
            StorageFrag(
                modifier = Modifier,
                activity = activity,
                exitStorageFrag = { navController.navigate(NavDestinations.FILE_LIST) },
                setInitialPath = { viewModel.updateCurrentPath(it) }
            )
        }
        composable(NavDestinations.FILE_LIST) {
            val progressFlow = remember { viewModel.uiState.map { it.progressState } }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val progressState = uiState.progressState
            val showProgress = progressState.progress.let { it != null && it < 100 }

            BackHandler {
                mViewModel.onBackPressed { navController.popBackStack() }
            }

            Column(modifier = Modifier) {
                OptionFrag(modifier = Modifier, activity = activity)
                FileListFrag(modifier = Modifier, viewModel = viewModel)
            }

            if (showProgress) {
                ProgressDialog(progressState = progressState)
            }

            LaunchedEffect(Unit) {
                progressFlow.collect { state ->
                    state.actionKey?.let { actionKey ->
                        viewModel.progressNoti(actionKey, state)
                    }
                }
            }
        }
    }
}
