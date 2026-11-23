package com.todokanai.composepracticenew.model

/** [SortMode]가 정렬에 쓰는 항목 속성. 파일 목록 항목과 압축 파일 안의 항목이 같은 정렬 규칙을 쓰도록 둘이 함께 구현한다. */
interface SortableEntry {
    /** 항목 이름. */
    val name: String

    /** 폴더 여부. */
    val isDirectory: Boolean

    /** 크기(bytes). 폴더이거나 크기를 모르면 null. */
    val sizeBytes: Long?

    /** 수정 시각(epoch millis). 모르면 null. */
    val lastModifiedMillis: Long?
}
