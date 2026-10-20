package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameError

/**
 * 새 폴더·이름 변경·압축 파일명 등 사용자가 입력한 파일 이름의 규칙을 정의하는 UseCase.
 * 로컬·원격 저장소에 같은 규칙을 적용한다. 앞뒤 공백은 잘라내지 않고 거부하며, 이름 중간의 공백은 허용한다.
 * 확장자 변경(예: "a.txt" → "a.md")과 이름 중간의 '.'은 허용한다.
 */
class FileNameUseCase {

    /**
     * 파일 이름을 검증한다.
     * @param name 사용자가 입력한 이름(확장자 포함)
     * @param suffix 저장 시 이름 뒤에 덧붙는 문자열(예: 압축 파일의 ".zip"). 길이 한도는 [name]+[suffix] 기준으로 판정한다
     * @return 위반한 규칙의 집합(검사 순서 유지). 비어 있으면 유효한 이름이다
     */
    fun validate(name: String, suffix: String = ""): Set<FileNameError> = buildSet {
        if (name.isBlank()) add(FileNameError.BLANK)
        else if (hasLeadingOrTrailingWhitespace(name)) add(FileNameError.LEADING_OR_TRAILING_WHITESPACE)
        if (name.any(::isPathSeparator)) add(FileNameError.CONTAINS_PATH_SEPARATOR)
        if (name.any(::isControlChar)) add(FileNameError.CONTAINS_CONTROL_CHAR)
        if (isReservedName(name)) add(FileNameError.RESERVED_NAME)
        if (exceedsMaxLength(name + suffix)) add(FileNameError.TOO_LONG)
    }

    /** 첫 글자나 마지막 글자가 공백 문자인지 판별한다. 공백뿐인 이름은 [FileNameError.BLANK]로 따로 다룬다. */
    private fun hasLeadingOrTrailingWhitespace(name: String): Boolean =
        name.first().isWhitespace() || name.last().isWhitespace()

    /** 경로 구분자인지 판별한다. */
    private fun isPathSeparator(c: Char): Boolean = c == '/' || c == '\\'

    /** 제어 문자(U+0000~U+001F, U+007F)인지 판별한다. */
    private fun isControlChar(c: Char): Boolean = c.code < 0x20 || c.code == 0x7F

    /** 이름 전체가 '.' 또는 '..'인지 판별한다. */
    private fun isReservedName(name: String): Boolean = name == "." || name == ".."

    /** UTF-8 인코딩 길이가 [MAX_NAME_BYTES]를 넘는지 판별한다. */
    private fun exceedsMaxLength(fullName: String): Boolean = fullName.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES

    private companion object {
        /** ext4·FAT 등 주요 파일시스템의 파일 이름 길이 한도. */
        const val MAX_NAME_BYTES = 255
    }
}
