package com.todokanai.composepracticenew.model

/** 이름 변경 시 같은 폴더에 새 이름과 같은 항목이 있을 때, 그 항목과의 관계로 정해지는 처리 방식. */
enum class RenameConflict {
    /** 같은 이름의 다른 항목이 없다. 그대로 이름을 바꾼다. */
    NONE,
    /** 파일을 같은 이름의 기존 파일 위로 바꾼다. 확인 후 기존 파일을 덮어쓴다. */
    FILE,
    /** 폴더를 같은 이름의 기존 폴더 위로 바꾼다. 확인 후 두 폴더를 합친다(겹치는 항목은 덮어쓰고 나머지는 유지). */
    FOLDER,
    /** 파일과 폴더처럼 종류가 다른 항목이 같은 이름으로 있다. 이름을 바꿀 수 없다. */
    TYPE_MISMATCH,
}
