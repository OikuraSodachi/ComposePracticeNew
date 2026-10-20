package com.todokanai.composepracticenew.model

/**
 * 파일 목록 정렬 모드의 저장 식별자, 표시 순서(선언 순서), 정렬 규칙을 한 곳에서 정의한다.
 * 새 모드를 추가하면 정렬 로직과 선택 목록 순서에 반영되며, 표시 문구는 app 레이어의 exhaustive `when`(SortDialog)이 누락을 컴파일 단계에서 강제한다.
 * @param id DataStore에 저장되는 문자열 식별자(기존 저장값과 호환되도록 변경 금지). 저장 전용이며 UI 표시·선택 판별에 쓰지 않는다
 * @param comparator 이 모드의 정렬 규칙
 */
enum class SortMode(val id: String, val comparator: Comparator<FileHolderItem>) {
    BY_DEFAULT("BY_DEFAULT", compareBy<FileHolderItem> { !it.isDirectory }.thenBy { it.name }),
    BY_NAME_ASCENDING("BY_NAME_ASCENDING", compareBy { it.name }),
    BY_NAME_DESCENDING("BY_NAME_DESCENDING", compareByDescending { it.name }),
    BY_SIZE_ASCENDING("BY_SIZE_ASCENDING", compareBy { it.sizeBytes }),
    BY_SIZE_DESCENDING("BY_SIZE_DESCENDING", compareByDescending { it.sizeBytes }),
    BY_TYPE_ASCENDING("BY_TYPE_ASCENDING", compareBy { it.extension() }),
    BY_TYPE_DESCENDING("BY_TYPE_DESCENDING", compareByDescending { it.extension() }),
    BY_DATE_ASCENDING("BY_DATE_ASCENDING", compareBy { it.lastModifiedMillis }),
    BY_DATE_DESCENDING("BY_DATE_DESCENDING", compareByDescending { it.lastModifiedMillis });

    companion object {
        /**
         * 저장된 [id]로부터 정렬 모드를 복원한다.
         * @param id [SortMode.id] 값
         * @return 일치하는 정렬 모드, 알 수 없는 값이면 [BY_DEFAULT]
         */
        fun fromId(id: String?): SortMode = entries.firstOrNull { it.id == id } ?: BY_DEFAULT
    }
}

private fun FileHolderItem.extension(): String = name.substringAfterLast('.', "")
