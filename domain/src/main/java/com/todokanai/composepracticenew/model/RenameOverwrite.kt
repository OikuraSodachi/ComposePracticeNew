package com.todokanai.composepracticenew.model

/**
 * 같은 이름의 같은 종류 항목 위로 이름을 바꿀 때(확인 후 덮어쓰기) 저장소에서 일어나는 일. 저장소의 동작이므로 이름 변경을 실행하는 UseCase가 정하고, 확인 다이얼로그는 이 값으로 문구만 고른다(#447).
 */
enum class RenameOverwrite {
    /** 기존 파일을 덮어쓴다. */
    REPLACE_FILE,

    /** 두 폴더를 합친다(겹치는 항목은 덮어쓰고 나머지는 유지). */
    MERGE_FOLDER,

    /** 결과가 저장소(서버) 동작에 따른다. */
    STORAGE_DEFINED,
}
