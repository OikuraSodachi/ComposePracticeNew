package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * 스토리지 접근 권한 확인 및 스토리지 목록 초기화를 담당하는 ViewModel.
 * @param getStorageListUseCase 물리 스토리지 목록을 조회·저장하는 UseCase
 * @param context 패키지명 조회에 사용하는 ApplicationContext
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    /** 스토리지 접근 권한 설정 화면으로 이동하라는 이벤트. Activity에서 수집해 startActivity()를 호출한다. */
    val navigationEvent: SharedFlow<Intent> = _navigationEvent.asSharedFlow()

    /**
     * MANAGE_APP_ALL_FILES_ACCESS 권한을 확인한다.
     * 허용된 경우 스토리지 목록을 로드하고, 미허용이면 설정 화면 이동 이벤트를 방출한다.
     * @return Unit
     */
    fun requestStorageManageAccess() {
        if (Environment.isExternalStorageManager()) {
            getStorageListUseCase.execute()
        } else {
            val intent = Intent().apply {
                action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                data = Uri.fromParts("package", context.packageName, null)
            }
            _navigationEvent.tryEmit(intent)
        }
    }
}
