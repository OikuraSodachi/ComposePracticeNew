package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.LocalTreeEntryKind
import com.todokanai.composepracticenew.model.FailureReason

/** 폴더 업로드가 로컬 트리 항목 하나에 할 일. 업로드와 업로드 전 이름 읽기가 같은 규칙을 쓰도록 [uploadActionOf] 한 곳에서만 정한다. */
internal sealed interface UploadAction {
    /** 원격에 폴더를 만든다(이미 있으면 그대로 쓴다). */
    data object CreateDirectory : UploadAction

    /** 원격에 파일을 올린다. */
    data object UploadFile : UploadAction

    /**
     * 올리지 않고 실패로 집계한다.
     * @param reason 올리지 않은 원인
     */
    data class Skip(val reason: FailureReason) : UploadAction
}

/**
 * [kind] 항목에 폴더 업로드가 할 일을 정한다. 링크는 지원 범위 밖이라, 나열하지 못한 폴더는 빈 폴더로 만들면 내용이 빠진 채 성공하므로(#422) 건너뛴다.
 * @param kind 로컬 트리 순회가 정한 항목의 종류
 * @return 업로드가 할 일
 */
internal fun uploadActionOf(kind: LocalTreeEntryKind): UploadAction = when (kind) {
    LocalTreeEntryKind.DIRECTORY -> UploadAction.CreateDirectory
    LocalTreeEntryKind.FILE -> UploadAction.UploadFile
    LocalTreeEntryKind.LINK -> UploadAction.Skip(FailureReason.UnsupportedLink())
    LocalTreeEntryKind.UNLISTED_DIRECTORY -> UploadAction.Skip(FailureReason.FolderUnreadable())
}
