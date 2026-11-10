package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.repository.StorageNameRules

/**
 * 새 폴더·이름 변경·압축 파일명 등 사용자가 입력한 파일 이름의 규칙을 정의하는 UseCase.
 * 앱이 모든 저장소에 강제하는 공통 규칙은 여기서 판정하고, 길이 한도처럼 저장소의 파일 시스템이 정하는 제약은 [storageNames]에 맡긴다.
 * 앞뒤 공백은 잘라내지 않고 거부하며, 이름 중간의 공백은 허용한다. 확장자 변경(예: "a.txt" → "a.md")과 이름 중간의 '.'은 허용한다.
 * @param storageNames 이름이 놓일 저장소의 제약을 판정하는 규칙 — 로컬·원격 화면마다 다루는 저장소의 규칙을 넘긴다
 */
class FileNameUseCase(private val storageNames: StorageNameRules) {

    /**
     * 파일 이름을 검증한다.
     * @param name 사용자가 입력한 이름(확장자 포함)
     * @param location 이름이 놓일 저장소의 경로 — 새 항목이면 만들 폴더, 이름 변경이면 그 항목 자신. 저장소의 제약을 고르는 데 쓴다
     * @param suffix 저장 시 이름 뒤에 덧붙는 문자열(예: 압축 파일의 ".zip"). 저장소의 제약은 [name]+[suffix] 기준으로 판정한다
     * @return 위반한 규칙의 집합(공통 규칙, 저장소의 제약 순서). 비어 있으면 유효한 이름이다
     */
    fun validate(name: String, location: String, suffix: String = ""): Set<FileNameError> = buildSet {
        if (name.isBlank()) add(FileNameError.BLANK)
        else if (hasLeadingOrTrailingWhitespace(name)) add(FileNameError.LEADING_OR_TRAILING_WHITESPACE)
        if (name.any(::isPathSeparator)) add(FileNameError.CONTAINS_PATH_SEPARATOR)
        if (name.any(::isControlChar)) add(FileNameError.CONTAINS_CONTROL_CHAR)
        if (isReservedName(name)) add(FileNameError.RESERVED_NAME)
        addAll(storageNames.violationsOf(name + suffix, location))
    }

    /**
     * 첫 글자나 마지막 글자가 공백 문자인지 판별한다. 공백뿐인 이름은 [FileNameError.BLANK]로 따로 다룬다.
     * @param name 공백뿐이 아닌(비어 있지 않은) 검사 대상 이름
     * @return 앞이나 뒤에 공백이 있으면 true
     */
    private fun hasLeadingOrTrailingWhitespace(name: String): Boolean =
        name.first().isWhitespace() || name.last().isWhitespace()

    /**
     * 경로 구분자인지 판별한다.
     * @param c 검사할 문자
     * @return '/' 또는 '\'이면 true
     */
    private fun isPathSeparator(c: Char): Boolean = c == '/' || c == '\\'

    /**
     * 화면에 보이지 않으면서 이름을 보이는 모습과 다르게 만들거나 줄을 바꾸는 제어 문자인지 판별한다.
     * @param c 검사할 문자
     * @return 제어 문자(C0·DEL·C1), 글자 방향 제어 문자([BIDI_CONTROLS]), 줄·문단 구분자([LINE_SEPARATORS])이면 true
     */
    private fun isControlChar(c: Char): Boolean {
        // 범위 밖:
        // 1) 폭 없는 문자(ZWJ U+200D, ZWNJ U+200C 등)는 이모지·일부 문자 체계의 정상 이름에 쓰이므로 허용한다. Unicode Cf 범주 전체를 거부하지 않는 이유다.
        // 2) 이 규칙은 사용자가 입력하는 이름에만 적용된다. 서버·외부 앱이 이미 만든 위장 이름의 표시는 목록 표시 레이어의 별도 과제다.
        // 3) FAT/exFAT(SD 카드·USB) 금지 문자(" * : < > ? |)와 끝의 '.'은 그 저장소의 제약이라 [storageNames]가 판정할 자리지만,
        //    지금은 볼륨의 파일 시스템을 구분하지 않아 검사하지 않는다. 해당 저장소에서는 검증 통과 후 mkdir/renameTo 단계에서 실패 알림으로 끝난다.
        return c.isISOControl() || c in BIDI_CONTROLS || c in LINE_SEPARATORS
    }

    /**
     * 이름 전체가 '.' 또는 '..'인지 판별한다.
     * @param name 검사할 이름
     * @return 현재·상위 디렉터리를 뜻하는 예약 이름이면 true
     */
    private fun isReservedName(name: String): Boolean = name == "." || name == ".."

    private companion object {
        /**
         * 글자 방향 제어 문자(Unicode PropList의 Bidi_Control 속성 전체).
         * 이름에 들어가면 "report[U+202E]fdp.apk"가 화면에 "reportkpa.pdf"로 보이는 것처럼 실제 확장자를 위장할 수 있다.
         */
        val BIDI_CONTROLS: Set<Char> = setOf('\u061C', '\u200E', '\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069')

        /** 줄 구분자(U+2028)와 문단 구분자(U+2029). 제어 문자인 '\n'처럼 화면에서 줄을 바꾼다. */
        val LINE_SEPARATORS: Set<Char> = setOf('\u2028', '\u2029')
    }
}
