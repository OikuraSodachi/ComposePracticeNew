package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import org.junit.Assert.assertEquals
import org.junit.Test

/** 화면 하나의 선택 상태가 허용된 전환으로만 바뀌고, 기본·전송 목적지 상태에 선택이 남지 않는지 검증하는 단위 테스트. */
class SelectionStateHolderTest {

    private val holder = SelectionStateHolder<SelectMode.LocalOperation, SelectMode.Download>()
    private val a = item("/a")
    private val b = item("/b")

    @Test
    fun `다중 선택을 시작하면 첫 항목만 선택된다`() {
        holder.startMultiSelect(a)

        assertEquals(SelectionState.MultiSelect(listOf(a)), holder.state.value)
    }

    @Test
    fun `작업 확인 단계로 가면 선택을 유지한다`() {
        holder.startMultiSelect(a)
        holder.add(b)

        holder.enterOperation(SelectMode.LocalOperation.Copy)

        assertEquals(SelectionState.Operation(SelectMode.LocalOperation.Copy, listOf(a, b)), holder.state.value)
    }

    @Test
    fun `작업 확인 단계에서 취소하면 선택 없이 기본 모드가 된다`() {
        holder.startMultiSelect(a)
        holder.enterOperation(SelectMode.LocalOperation.Move)

        holder.reset()

        assertEquals(SelectionState.Default, holder.state.value)
        assertEquals(emptyList<FileHolderItem>(), holder.state.value.selected)
    }

    @Test
    fun `전송을 받으면 이 화면의 선택을 버리고 대기 목록만 가진다`() {
        holder.startMultiSelect(a)

        holder.receive(SelectMode.Download, listOf(b))

        assertEquals(SelectionState.Receiving(SelectMode.Download, listOf(b)), holder.state.value)
        assertEquals(emptyList<FileHolderItem>(), holder.state.value.selected)
    }

    @Test
    fun `전송 목적지 확인에서 취소하면 대기 목록도 사라진다`() {
        holder.receive(SelectMode.Download, listOf(b))

        holder.reset()

        assertEquals(SelectionState.Default, holder.state.value)
    }

    @Test
    fun `다중 선택이 아니면 선택 편집과 작업 진입은 상태를 바꾸지 않는다`() {
        holder.add(a)
        holder.replaceSelection(listOf(a, b))
        holder.enterOperation(SelectMode.LocalOperation.Copy)
        assertEquals(SelectionState.Default, holder.state.value)

        holder.receive(SelectMode.Download, listOf(b))
        holder.add(a)
        holder.enterOperation(SelectMode.LocalOperation.Copy)
        assertEquals(SelectionState.Receiving(SelectMode.Download, listOf(b)), holder.state.value)
    }

    @Test
    fun `다중 선택 중 항목을 빼고 범위로 바꿀 수 있다`() {
        holder.startMultiSelect(a)
        holder.add(b)
        holder.remove(a)
        assertEquals(SelectionState.MultiSelect(listOf(b)), holder.state.value)

        holder.replaceSelection(listOf(a, b))
        assertEquals(SelectionState.MultiSelect(listOf(a, b)), holder.state.value)
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "")
}
