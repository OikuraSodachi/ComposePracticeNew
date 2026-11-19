package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.tools.NewFolderNameDefaults
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.viewmodel.EntryNamingStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [EntryNamingStateHolder]가 새 폴더의 대상 폴더와 이름 검증을 맡는지 검증하는 단위 테스트(#452).
 * 대상 폴더는 화면의 현재 폴더를 따르고, 검증은 다이얼로그를 연 때 정한 대상 폴더를 이름이 놓일 위치로 넘긴다.
 */
class EntryNamingNewFolderTest {

    /** 현재 폴더를 테스트가 정하는 탐색 저장소. */
    private val nav = FakeNavigator()

    /** 이름 규칙에 넘어온 (이름, 위치) 쌍. */
    private val validated = mutableListOf<Pair<String, String>>()

    /** 이름 규칙 호출을 기록하는 람다로 만든 검증 대상. */
    private val holder = EntryNamingStateHolder(
        validateEntryName = { name, location -> validated += name to location; setOf(FileNameError.BLANK) },
        renameOverwriteOf = { RenameOverwrite.STORAGE_DEFINED },
        conflictCheckUseCase = ConflictCheckUseCase(RecordingOperationNotices(), ExactNameMatching),
        folder = FileNavigatorUseCase(nav, nav),
        folderNames = object : NewFolderNameDefaults {
            override val defaultNewFolderName = "새 폴더"
        },
    )

    @Test
    fun `새 폴더 대상은 현재 폴더를 따른다`() {
        assertNull(holder.newFolderTarget.value)

        nav.moveTo("/storage/0/music")

        assertEquals("/storage/0/music", holder.newFolderTarget.value)
    }

    @Test
    fun `새 폴더 이름 검증은 대상 폴더를 이름이 놓일 위치로 넘기고 규칙의 결과를 돌려준다`() {
        nav.moveTo("/storage/0/other")

        val errors = holder.validateNewFolderName(target = "/storage/0/music", name = "photos")

        assertEquals(listOf("photos" to "/storage/0/music"), validated)
        assertEquals(setOf(FileNameError.BLANK), errors)
    }
}
