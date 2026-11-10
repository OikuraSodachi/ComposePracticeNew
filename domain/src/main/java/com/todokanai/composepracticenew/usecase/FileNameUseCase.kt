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
     * 제어 문자(U+0000~U+001F, U+007F)인지 판별한다.
     * @param c 검사할 문자
     * @return C0 제어 문자 또는 DEL이면 true
     */
    private fun isControlChar(c: Char): Boolean {
        // 알려진 한계(PR #207 리뷰 4번, 미해결):
        // 1) C1 제어 문자(U+0080~U+009F)는 검사하지 않아 통과한다. 대응안: c.isISOControl()로 C0·DEL·C1을 함께 거부.
        // 2) bidi 서식 문자(U+061C, U+200E/U+200F, U+202A~U+202E, U+2066~U+2069)를 통과시켜 표시 이름 위장이 가능하다.
        //    예: "report[U+202E]fdp.apk"는 화면에 "reportkpa.pdf"로 보이지만 실제 확장자는 .apk라, 열면 설치 화면이 뜰 수 있다.
        //    대응안: 위 목록만 명시 거부(BIDI_CONTROLS). Unicode Cf 범주 전체 거부는 이모지 ZWJ(U+200D) 등 정상 이름까지 막으므로 쓰지 않는다.
        //    이 정책은 사용자가 입력하는 이름에만 적용되므로, 서버·외부 앱이 이미 만든 위장 이름의 표시는 목록 표시 레이어의 별도 과제다.
        // 3) FAT/exFAT(SD 카드·USB) 금지 문자(" * : < > ? |)와 끝의 '.'은 그 저장소의 제약이라 [storageNames]가 판정할 자리지만,
        //    지금은 볼륨의 파일 시스템을 구분하지 않아 검사하지 않는다. 해당 저장소에서는 검증 통과 후 mkdir/renameTo 단계에서 실패 알림으로 끝난다.
        return c.code < 0x20 || c.code == 0x7F
    }

    /**
     * 이름 전체가 '.' 또는 '..'인지 판별한다.
     * @param name 검사할 이름
     * @return 현재·상위 디렉터리를 뜻하는 예약 이름이면 true
     */
    private fun isReservedName(name: String): Boolean = name == "." || name == ".."
}
