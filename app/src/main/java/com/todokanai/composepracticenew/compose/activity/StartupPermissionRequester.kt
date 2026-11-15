package com.todokanai.composepracticenew.compose.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 앱 시작 때 없는 권한(알림, 전체 파일 접근)을 요청한다. 허가 여부에 따라 앱의 다른 동작을 바꾸지 않는다.
 * 런처를 등록하므로 Activity가 STARTED가 되기 전(속성 초기화나 onCreate)에 만든다.
 * @param activity 권한을 요청하고 설정 화면을 여는 Activity
 */
internal class StartupPermissionRequester(private val activity: ComponentActivity) {

    /** 알림 권한 요청 창을 여는 런처. 알림 권한은 없어도 앱이 동작하므로(진행 알림만 보이지 않는다) 결과를 받아 할 일이 없다. */
    private val notificationPermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** 허가되지 않은 권한마다 요청 창이나 설정 화면을 연다. */
    fun requestMissing() {
        if (needsNotificationPermission()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!Environment.isExternalStorageManager()) openAllFilesAccessSettings()
    }

    /** 알림 권한이 런타임 권한인 버전(Android 13+)에서 아직 허가되지 않았으면 true. */
    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** 이 앱의 전체 파일 접근 설정 화면을 연다. 전체 파일 접근은 요청 창이 없는 특별 권한이라 설정 화면에서만 허가할 수 있다. */
    private fun openAllFilesAccessSettings() {
        activity.startActivity(Intent().apply {
            action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            data = Uri.fromParts("package", activity.packageName, null)
        })
    }
}
