package com.todokanai.composepracticenew.model

import java.text.Normalizer

/**
 * 파일 이름 검색어. 이름에 검색어가 들어 있으면 일치로 보며, 대소문자를 구분하지 않는다.
 * 같은 글자도 조합형(NFD — macOS에서 만든 한글 이름 등)과 완성형(NFC — 키보드 입력)으로 다르게 저장될 수 있으므로 둘 다 NFC로 맞춰 비교한다.
 * 비어 있거나 공백뿐인 검색어는 만들 수 없다([of]가 null을 돌려준다).
 * @param text 사용자가 입력한 검색어
 */
class SearchQuery private constructor(val text: String) {

    /** NFC로 맞춘 [text]. 이름마다 다시 맞추지 않도록 한 번만 만든다. */
    private val normalizedText: String = normalized(text)

    /**
     * 이름 [name]이 이 검색어와 맞는지 판별한다.
     * @param name 판별할 항목 이름
     * @return 이름에 검색어가 들어 있으면 true
     */
    fun matches(name: String): Boolean = normalized(name).contains(normalizedText, ignoreCase = true)

    companion object {

        /**
         * 입력한 검색어로 [SearchQuery]를 만든다.
         * @param text 사용자가 입력한 검색어
         * @return 검색어. 비어 있거나 공백뿐이면 null — 모든 항목과 맞는 검색이 시작되지 않도록 한다
         */
        fun of(text: String): SearchQuery? = text.takeIf { it.isNotBlank() }?.let(::SearchQuery)

        /** [value]를 NFC로 맞춘다. */
        private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
    }
}
