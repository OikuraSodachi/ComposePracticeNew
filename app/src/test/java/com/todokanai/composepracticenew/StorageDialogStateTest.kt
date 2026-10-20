package com.todokanai.composepracticenew

import androidx.compose.runtime.saveable.SaverScope
import com.todokanai.composepracticenew.compose.frag.StorageDialogState
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [StorageDialogState.Saver]가 구성 변경 시 세 다이얼로그의 표시 상태를 그대로 복원하는지 검증하는 단위 테스트. */
class StorageDialogStateTest {

    /** 모든 값을 저장 가능하다고 응답하는 테스트용 SaverScope. */
    private val scope = SaverScope { true }

    @Test
    fun `추가·편집·삭제 상태를 저장 후 그대로 복원한다`() {
        val state = StorageDialogState().apply {
            openAdd()
            openEdit(item(id = 7))
            openDelete(item(id = 9))
        }

        val restored = roundTrip(state)

        assertTrue(restored.showAddDialog)
        assertEquals(7L, restored.editingId)
        assertEquals(9L, restored.deletingId)
    }

    @Test
    fun `닫힌 상태는 닫힌 상태로 복원한다`() {
        val restored = roundTrip(StorageDialogState())

        assertFalse(restored.showAddDialog)
        assertNull(restored.editingId)
        assertNull(restored.deletingId)
    }

    private fun roundTrip(state: StorageDialogState): StorageDialogState {
        val saved = with(StorageDialogState.Saver) { scope.save(state) }!!
        return StorageDialogState.Saver.restore(saved)!!
    }

    private fun item(id: Long) = RemoteStorageItem(
        id = id, name = "n", address = "a", port = 21, userId = "u", password = "p", encoding = "UTF-8"
    )
}
