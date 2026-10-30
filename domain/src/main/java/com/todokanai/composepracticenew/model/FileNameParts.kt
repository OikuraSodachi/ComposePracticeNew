package com.todokanai.composepracticenew.model

/**
 * 파일 이름을 기본 이름과 확장자로 나누고, 같은 이름이 있을 때 번호를 붙이는 규칙.
 * 기본 이름 제안·입력란 선택 범위·압축 파일 생성이 같은 규칙을 쓰도록 한 곳에 둔다.
 */
object FileNameParts {

    /**
     * [name]에서 확장자가 시작하는 위치(점의 위치)를 반환한다. 첫 글자의 점은 숨김 파일 표시로 보아 확장자로 나누지 않는다(예: `.bashrc`).
     * @param name 파일 이름
     * @return 확장자의 점 위치. 확장자가 없으면 [name]의 길이
     */
    fun baseNameEnd(name: String): Int = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length

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
