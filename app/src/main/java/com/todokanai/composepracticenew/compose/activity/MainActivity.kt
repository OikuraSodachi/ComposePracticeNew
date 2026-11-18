package com.todokanai.composepracticenew.compose.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import com.todokanai.composepracticenew.navigation.AppNavHost
import com.todokanai.composepracticenew.ui.theme.ComposePracticeTheme
import com.todokanai.composepracticenew.viewmodel.ProgressDialogViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 알림 클릭으로 띄울 진행률 다이얼로그를 받는 ViewModel. AppNavHost가 이 Activity를 owner로 같은 인스턴스를 쓴다. */
    private val progressDialogViewModel: ProgressDialogViewModel by viewModels()
    /** 시작 때 없는 권한을 요청한다. 런처 등록이 STARTED 전이어야 하므로 속성으로 만든다. */
    private val permissions = StartupPermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(viewModelStoreOwner = this, onExitApp = ::finishAndRemoveTask)
        }

        permissions.requestMissing()

        // 시스템은 회전·프로세스 복원·최근 앱 실행 때도 처음 실행한 intent를 다시 넘기므로, 새로 받은 경우만 처리한다
        if (isNewLaunchIntent(hasSavedState = savedInstanceState != null, intentFlags = intent.flags)) {
            showProgressDialogOf(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showProgressDialogOf(intent)
    }

    /** [intent]가 진행 알림 클릭이면 그 작업의 진행률 다이얼로그를 띄운다. */
    private fun showProgressDialogOf(intent: Intent?) {
        val request = progressDialogRequestOf(intent) ?: return
        progressDialogViewModel.requestShowProgressDialog(request.operationTypeId, request.instanceId)
    }
}

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
