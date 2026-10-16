package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity 스코프의 앱 전역 관심사(스토리지 목록 초기화·알림 클릭 다이얼로그 복원·작업 완료/실패 이벤트·앱 종료)를 담당하는 ViewModel.
 * @param getStorageListUseCase 물리 스토리지 목록을 로드·갱신하는 UseCase
 * @param progressUseCase 파일 작업 완료·실패 이벤트를 제공하는 UseCase
 * @param exitHandler 연결 서비스를 중단하고 앱 종료 이벤트를 발행하는 핸들러
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase,
    progressUseCase: ProgressUseCase,
    private val exitHandler: AppExitHandler
) : ViewModel() {

    private val _showProgressDialog = MutableStateFlow(false)
    /** 알림 클릭으로 로컬 작업 진행률 다이얼로그를 강제 재표시해야 하는지 여부. */
    val showProgressDialog: StateFlow<Boolean> = _showProgressDialog.asStateFlow()

    private val _showRemoteProgressDialog = MutableStateFlow(false)
    /** 원격 전송 알림 클릭 시 REMOTE_FILE_LIST 화면의 진행률 다이얼로그를 다시 표시해야 하는지 여부. */
    val showRemoteProgressDialog: StateFlow<Boolean> = _showRemoteProgressDialog.asStateFlow()

    /** 파일 작업 실패 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationError: SharedFlow<String> = progressUseCase.operationErrors
    /** 파일 작업 완료 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationCompletion: SharedFlow<String> = progressUseCase.operationCompletions

    /** 앱을 종료하라는 이벤트. 화면 호스트에서 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit> = exitHandler.exitEvent

    /**
     * 스토리지 목록을 로드해 UI 상태를 갱신한다.
     * @return Unit
     */
    fun loadStorages() = viewModelScope.launch(Dispatchers.IO) { getStorageListUseCase.execute() }

    /**
     * 알림 클릭으로 전달된 작업 종류 id에 맞는 진행률 다이얼로그 재표시를 요청한다.
     * 원격 전송이면 원격 다이얼로그, 그 외(식별 불가 포함)는 로컬 다이얼로그를 트리거한다.
     * @param operationTypeId 알림 Intent로 전달된 FileOperationType.id
     */
    fun requestShowProgressDialog(operationTypeId: Int) {
        if (FileOperationType.fromId(operationTypeId)?.isRemote == true) {
            _showRemoteProgressDialog.value = true
        } else {
            _showProgressDialog.value = true
        }
    }

    /** 로컬 작업 진행률 다이얼로그가 표시됐음을 알려 트리거를 초기화한다. */
    fun onProgressDialogShown() { _showProgressDialog.value = false }

    /** 원격 전송 진행률 다이얼로그가 표시됐음을 알려 트리거를 초기화한다. */
    fun onRemoteProgressDialogShown() { _showRemoteProgressDialog.value = false }

    /** 연결 서비스를 중단하고 앱 종료 이벤트를 발행한다. */
    fun exit() = exitHandler.execute()
}
