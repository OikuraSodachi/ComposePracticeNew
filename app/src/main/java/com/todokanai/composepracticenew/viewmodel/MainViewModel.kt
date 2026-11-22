package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.LoadStorageListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity 스코프에서 앱의 시작과 종료(시작 때 스토리지 목록 불러오기, 앱 종료)를 맡는 ViewModel.
 * 진행률 다이얼로그는 [ProgressDialogViewModel], 앱 알림은 [AppNoticeViewModel]이 맡는다(#448).
 * @param loadStorageListUseCase 물리 스토리지 목록을 로드·갱신하는 UseCase
 * @param exitHandler 원격 연결을 종료하고 앱 종료 이벤트를 발행하는 핸들러
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val loadStorageListUseCase: LoadStorageListUseCase,
    private val exitHandler: AppExitHandler
) : ViewModel() {

    /** 앱을 종료하라는 이벤트. 화면 호스트에서 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit> = exitHandler.exitEvent

    init {
        // 볼륨 목록은 권한 없이 얻을 수 있으므로 권한 허가를 기다리지 않는다 — 권한에 묶으면 허가하고 돌아와도 빈 목록이 남는다(#364)
        viewModelScope.launch(Dispatchers.IO) { loadStorageListUseCase.execute() }
    }

    /** 연결 서비스를 중단하고 앱 종료 이벤트를 발행한다. */
    fun exit() = exitHandler.execute()
}
