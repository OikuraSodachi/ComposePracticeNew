package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.ui.model.AttributesInfo
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
 * 정보 다이얼로그의 표시 상태와 선택 항목의 정보 계산(속성, 크기, 폴더의 하위 항목 수)을 맡는다.
 * 다이얼로그를 닫거나 다른 선택으로 다시 열면 진행 중인 계산을 취소한다.
 * 로컬 목록 화면 전용이다 — [FileInfoUseCase]는 로컬 파일 시스템 구현에 바인딩돼 있다.
 * @param fileInfoUseCase 선택 항목의 속성·크기·하위 항목 수를 구하는 UseCase
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
     * [items]의 정보 다이얼로그를 열고 크기를, 항목이 하나면 속성(폴더면 이어서 하위 항목 수)을 저장소에서 읽기 시작한다.
     * @param items 정보를 표시할 선택 항목 목록
     */
    fun show(items: List<FileHolderItem>) {
        loadJob?.cancel()
        _summary.value = SelectionSummary.loading(items, locationOf = { fileInfoUseCase.locationOf(it.toDomain()) })
        loadJob = viewModelScope.launch {
            launch { loadSize(items) }
            items.singleOrNull()?.let { launch { loadAttributes(it) } }
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

    /** [item]의 속성을 읽어 표시 중인 단일 항목 상세에 반영하고, 지금 폴더면 하위 항목 수를 센다. */
    private suspend fun loadAttributes(item: FileHolderItem) {
        val result = fileInfoUseCase.readAttributes(item.toDomain())
        _summary.update { summary -> withSingle(summary) { it.copy(attributes = AttributesInfo.from(result, item.name)) } }
        if (isDirectory(result)) loadChildCount(item)
    }

    /** 폴더 [directory]의 하위 항목 수를 구해 표시 중인 단일 항목 상세에 반영한다. */
    private suspend fun loadChildCount(directory: FileHolderItem) {
        _summary.update { summary -> withSingle(summary) { it.copy(childCount = ChildCountInfo.Loading) } }
        val count = ChildCountInfo.from(fileInfoUseCase.countChildren(directory.toDomain()))
        _summary.update { summary -> withSingle(summary) { it.copy(childCount = count) } }
    }

    /** 속성 읽기 결과가 폴더인지. 읽지 못했으면 false다. */
    private fun isDirectory(result: IoResult<ItemAttributes>): Boolean = result is IoResult.Success && result.value.isDirectory

    /** 단일 항목 상세면 [change]를 적용하고, 아니면(닫혔거나 여러 항목) 그대로 둔다. */
    private fun withSingle(summary: SelectionSummary?, change: (SelectionSummary.Single) -> SelectionSummary.Single): SelectionSummary? =
        if (summary is SelectionSummary.Single) change(summary) else summary
}
