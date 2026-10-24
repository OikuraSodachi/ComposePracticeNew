package com.todokanai.composepracticenew.compose.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import androidx.lifecycle.ViewModelStoreOwner
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_INSTANCE_ID
import com.todokanai.composepracticenew.navigation.AppNavHost
import com.todokanai.composepracticenew.ui.theme.ComposePracticeTheme
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var activityResult: ActivityResultLauncher<String>
    private val mViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(viewModelStoreOwner = this, onExitApp = ::finishAndRemoveTask)
        }

        checkAndRequestPermissions()
        handleStorageAccess()

        activityResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) finish()
            }

        // 시스템은 회전·프로세스 복원·최근 앱 실행 때도 처음 실행한 intent를 다시 넘기므로, 새로 받은 경우만 처리한다
        if (isNewLaunchIntent(hasSavedState = savedInstanceState != null, intentFlags = intent.flags)) {
            handleProgressIntent(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        if (!checkWritePermission()) requestWritePermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun handleStorageAccess() {
        if (Environment.isExternalStorageManager()) {
            mViewModel.loadStorages()
        } else {
            // TODO: startActivity 후 복귀 콜백이 없어 권한 허가 후에도 loadStorages()가 호출되지 않음.
            //  registerForActivityResult(StartActivityForResult)로 교체하고 복귀 시 loadStorages() 재호출 필요.
            startActivity(Intent().apply {
                action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                data = Uri.fromParts("package", packageName, null)
            })
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
        val instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, -1)
        if (actionKey != -1 && instanceId != -1) mViewModel.requestShowProgressDialog(actionKey, instanceId)
    }
}

/**
 * onCreate가 받은 intent가 새로 전달된 것인지 판정한다.
 * 저장 상태가 있으면(회전 등 구성 변경, 프로세스 복원) 또는 최근 앱에서 다시 실행했으면 처음 실행한 intent를 다시 받은 것이다.
 * @param hasSavedState onCreate의 savedInstanceState가 null이 아닌지
 * @param intentFlags onCreate가 받은 intent의 flags
 * @return 새로 전달된 intent이면 true
 */
internal fun isNewLaunchIntent(hasSavedState: Boolean, intentFlags: Int): Boolean =
    !hasSavedState && (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

/** 앱 테마를 적용하고 navigation host를 표시하는 루트 화면. */
@Composable
private fun HomeScreen(viewModelStoreOwner: ViewModelStoreOwner, onExitApp: () -> Unit) {
    ComposePracticeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavHost(viewModelStoreOwner = viewModelStoreOwner, onExitApp = onExitApp)
        }
    }
}
