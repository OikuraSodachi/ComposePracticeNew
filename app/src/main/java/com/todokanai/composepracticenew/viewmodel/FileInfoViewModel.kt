package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.ui.model.SizeInfo
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 정보 다이얼로그의 표시 상태와 선택 항목의 정보 계산(크기, 폴더의 하위 항목 수)을 맡는다.
 * 다이얼로그를 닫거나 다른 선택으로 다시 열면 진행 중인 계산을 취소한다.
 * 로컬 목록 화면 전용이다 — [FileInfoUseCase]는 로컬 파일 시스템 구현에 바인딩돼 있다.
 * @param fileInfoUseCase 선택 항목의 크기·하위 항목 수를 구하는 UseCase
 */
@HiltViewModel
class FileInfoViewModel @Inject constructor(
    private val fileInfoUseCase: FileInfoUseCase
) : ViewModel() {

    /** [summary]의 변경 가능한 원본. */
    private val _summary = MutableStateFlow<SelectionSummary?>(null)
    /** 정보 다이얼로그에 표시할 선택 요약. null이면 다이얼로그를 표시하지 않는다. */
    val summary: StateFlow<SelectionSummary?> = _summary.asStateFlow()

    /** 지금 표시 중인 요약의 정보 계산. 다시 열거나 닫으면 취소한다. */
    private var loadJob: Job? = null

    /**
     * [items]의 정보 다이얼로그를 열고 크기와(폴더 하나면) 하위 항목 수 계산을 시작한다.
     * @param items 정보를 표시할 선택 항목 목록
     */
    fun show(items: List<FileHolderItem>) {
        loadJob?.cancel()
        _summary.value = SelectionSummary.loading(items)
        loadJob = viewModelScope.launch {
            launch { loadSize(items) }
            singleDirectoryOf(items)?.let { launch { loadChildCount(it) } }
        }
    }

    /** 정보 다이얼로그를 닫고 진행 중인 계산을 취소한다. */
    fun dismiss() {
        loadJob?.cancel()
        loadJob = null
        _summary.value = null
    }

    /** [items]의 크기를 구해 표시 중인 요약에 반영한다. */
    private suspend fun loadSize(items: List<FileHolderItem>) {
        val size = SizeInfo.from(fileInfoUseCase.measureSize(items.map { it.toDomain() }))
        _summary.update { it?.withSize(size) }
    }

    /** 폴더 [directory]의 하위 항목 수를 구해 표시 중인 단일 항목 상세에 반영한다. */
    private suspend fun loadChildCount(directory: FileHolderItem) {
        val count = ChildCountInfo.from(fileInfoUseCase.countChildren(directory.toDomain()))
        _summary.update { summary -> withChildCount(summary, count) }
    }

    /** 선택이 폴더 하나뿐이면 그 폴더, 아니면 null. */
    private fun singleDirectoryOf(items: List<FileHolderItem>): FileHolderItem? = items.singleOrNull()?.takeIf { it.isDirectory }

    /** 단일 항목 상세면 하위 항목 수를 바꾸고, 아니면(닫혔거나 여러 항목) 그대로 둔다. */
    private fun withChildCount(summary: SelectionSummary?, count: ChildCountInfo): SelectionSummary? =
        if (summary is SelectionSummary.Single) summary.copy(childCount = count) else summary
}
