package com.todokanai.composepracticenew.model

/**
 * 파일 이름을 기본 이름과 확장자로 나누고, 같은 이름이 있을 때 번호를 붙이는 규칙.
 * 기본 이름 제안·입력란 선택 범위·압축 파일 생성·'종류' 정렬·정보 다이얼로그의 종류 표시가 같은 규칙을 쓰도록 한 곳에 둔다.
 */
object FileNameParts {

    /**
     * [name]에서 확장자가 시작하는 위치(점의 위치)를 반환한다. 첫 글자의 점은 숨김 파일 표시로 보아 확장자로 나누지 않는다(예: `.bashrc`).
     * @param name 파일 이름
     * @return 확장자의 점 위치. 확장자가 없으면 [name]의 길이
     */
    fun baseNameEnd(name: String): Int = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length

    /**
     * [name]의 확장자를 점 없이 반환한다. [baseNameEnd]와 같은 규칙이라 숨김 파일의 첫 점은 확장자가 아니다(예: `.bashrc`는 확장자 없음).
     * @param name 파일 이름
     * @return 점을 뺀 확장자. 확장자가 없거나 이름이 점으로 끝나면 null
     */
    fun extensionOf(name: String): String? = name.substring(baseNameEnd(name)).removePrefix(".").takeIf { it.isNotEmpty() }

    /**
     * [baseName]에 번호 [index]를 붙인 이름을 만든다. 0이면 번호 없이 원래 이름이다.
     * @param baseName 확장자를 뺀 이름
     * @param extension 점을 포함한 확장자(없으면 빈 문자열)
     * @param index 붙일 번호
     * @return `이름확장자` 또는 `이름 (n)확장자`
     */
    fun numbered(baseName: String, extension: String, index: Int): String =
        if (index == 0) "$baseName$extension" else "$baseName ($index)$extension"
}
