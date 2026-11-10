package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileNameError

/** ext4·FAT 등 주요 파일시스템의 파일 이름 길이 한도(UTF-8 바이트). */
internal const val COMMON_MAX_NAME_BYTES = 255

/**
 * [fullName]의 UTF-8 인코딩 길이가 [maxBytes]를 넘는지 판정한다. PathAwareFileSystem 구현들이 이름 길이 제약에 함께 쓴다.
 * @param fullName 저장될 전체 이름(덧붙는 확장자 포함)
 * @param maxBytes 이름 길이 한도(UTF-8 바이트)
 * @return 한도를 넘으면 [FileNameError.TOO_LONG]만 담은 집합, 아니면 빈 집합
 */
internal fun nameByteLimitViolations(fullName: String, maxBytes: Int = COMMON_MAX_NAME_BYTES): Set<FileNameError> =
    if (fullName.toByteArray(Charsets.UTF_8).size > maxBytes) setOf(FileNameError.TOO_LONG) else emptySet()
