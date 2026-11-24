package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import java.text.DateFormat

/**
 * 압축 파일 내용 보기 화면의 행 하나. 크기·날짜는 표시용 문자열로 포맷해 둔다.
 * @param name 항목 이름(위험한 경로 항목이면 압축 파일에 적힌 원래 경로)
 * @param kind 항목 종류
 * @param size 크기 표시 문자열. 크기가 없거나 모르면 빈 문자열
 * @param lastModified 수정 시각 표시 문자열. 모르면 빈 문자열
 * @param key 항목을 가리키는 키 — 이름이 같은 항목끼리도 달라 선택과 목록 키에 쓴다
 */
data class ArchiveEntryRow(
    val name: String,
    val kind: ArchiveEntryRowKind,
    val size: String,
    val lastModified: String,
    val key: ArchiveEntryKey,
) {
    /** 아이콘을 고르는 항목 종류. 이름과 폴더 여부에서 생성 시 한 번 정한다. */
    val fileType: FileType = FileType.of(name, kind == ArchiveEntryRowKind.FOLDER)

    /** 이름이 점으로 시작하는 숨김 항목인지 여부. */
    val isHidden: Boolean = FileNameParts.isHidden(name)

    companion object {
        /**
         * 정렬한 domain 항목들을 화면 행으로 바꾼다. 날짜 포맷터는 목록당 1개만 만들어 재사용한다.
         * @param entries 정렬한 domain 항목들
         * @return 화면 행 목록
         */
        fun fromList(entries: List<ArchiveEntry>): List<ArchiveEntryRow> {
            // DateFormat은 스레드 안전하지 않으므로 공유하지 않고 변환 1회 범위에서만 재사용
            val dateFormat = DateFormat.getDateTimeInstance()
            return entries.map { entry ->
                ArchiveEntryRow(
                    name = entry.name,
                    kind = kindOf(entry),
                    size = entry.sizeBytes?.let(::readableFileSize_td) ?: "",
                    lastModified = entry.lastModifiedMillis?.let(dateFormat::format) ?: "",
                    key = entry.key,
                )
            }
        }

        /**
         * domain 항목의 행 종류를 정한다.
         * @param entry domain 항목
         * @return 행 종류
         */
        private fun kindOf(entry: ArchiveEntry): ArchiveEntryRowKind = when (entry) {
            is ArchiveEntry.Folder -> ArchiveEntryRowKind.FOLDER
            is ArchiveEntry.File -> if (entry.isEncrypted) ArchiveEntryRowKind.ENCRYPTED_FILE else ArchiveEntryRowKind.FILE
            is ArchiveEntry.Link -> ArchiveEntryRowKind.LINK
            is ArchiveEntry.UnsafePath -> ArchiveEntryRowKind.UNSAFE_PATH
        }
    }
}

/** 내용 보기 화면 행의 종류. 표시 문구와 누를 수 있는지를 정한다. */
enum class ArchiveEntryRowKind {
    /** 들어갈 수 있는 폴더. */
    FOLDER,
    /** 파일. */
    FILE,
    /** 내용이 암호화된 파일. */
    ENCRYPTED_FILE,
    /** 링크(압축 해제하지 않는다). */
    LINK,
    /** 압축 해제 위치 밖을 가리키는 경로(압축 해제하지 않는다). */
    UNSAFE_PATH,
}
