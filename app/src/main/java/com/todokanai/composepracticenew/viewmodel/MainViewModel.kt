package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 스토리지 목록 초기화를 담당하는 ViewModel.
 * @param getStorageListUseCase 물리 스토리지 목록을 로드·갱신하는 UseCase
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase
) : ViewModel() {

    /**
     * 스토리지 목록을 로드해 UI 상태를 갱신한다.
     * @return Unit
     */
    fun loadStorages() = getStorageListUseCase.execute()
}
