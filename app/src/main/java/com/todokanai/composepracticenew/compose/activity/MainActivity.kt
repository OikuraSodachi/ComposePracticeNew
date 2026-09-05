package com.todokanai.composepracticenew.compose.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.navigation.AppNavHost
import com.todokanai.composepracticenew.ui.theme.ComposePracticeTheme
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var activityResult: ActivityResultLauncher<String>
    private val mViewModel: MainViewModel by viewModels()
    private val fViewModel: FileListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(activity = this)
        }

        mViewModel.getPermission(this)

        activityResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted)
                    finish()
            }

        handleProgressIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleProgressIntent(intent)
    }

    private fun handleProgressIntent(intent: Intent?) {
        val actionKey = intent?.getIntExtra(EXTRA_ACTION_KEY, -1) ?: return
        if (actionKey != -1) fViewModel.requestShowProgressDialog(actionKey)
    }
}

@Composable
private fun HomeScreen(activity: MainActivity) {
    ComposePracticeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavHost(activity = activity)
        }
    }
}
