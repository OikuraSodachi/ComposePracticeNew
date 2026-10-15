package com.todokanai.composepracticenew.compose.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.navigation.AppNavHost
import com.todokanai.composepracticenew.ui.theme.ComposePracticeTheme
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var activityResult: ActivityResultLauncher<String>
    private val mViewModel: MainViewModel by viewModels()
    private val fViewModel: FileListViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(activity = this)
        }

        lifecycleScope.launch {
            mViewModel.navigationEvent.collect { intent -> startActivity(intent) }
        }

        checkAndRequestPermissions()
        mViewModel.requestStorageManageAccess()

        activityResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) finish()
            }

        handleProgressIntent(intent)
    }

    private fun checkAndRequestPermissions() {
        if (!checkWritePermission()) requestWritePermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun checkWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestWritePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, getString(R.string.toast_storage_permission), Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 112)
        }
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
