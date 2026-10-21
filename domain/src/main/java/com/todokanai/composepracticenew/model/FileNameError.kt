package com.todokanai.composepracticenew.model

/**
 * 파일·폴더 이름이 거부되는 사유. 로컬·원격 저장소에 공통으로 적용한다.
 * [EXISTS_AS_OTHER_TYPE]만 폴더의 현재 내용에 따라 정해지며, 나머지는 이름 자체만으로 정해진다.
 */
enum class FileNameError {
    /** 이름이 비어 있거나 공백뿐이다. */
    BLANK,

    /** 이름 앞이나 뒤에 공백이 있다. 이름 중간의 공백은 허용한다. */
    LEADING_OR_TRAILING_WHITESPACE,

    /** 경로 구분자('/' 또는 '\')를 포함한다 — 다른 경로로의 이동·생성이 되는 것을 막는다. */
    CONTAINS_PATH_SEPARATOR,

    /** 제어 문자(U+0000~U+001F, U+007F)를 포함한다. */
    CONTAINS_CONTROL_CHAR,

    /** 이름 전체가 현재·상위 디렉터리를 뜻하는 예약 이름('.' 또는 '..')이다. */
    RESERVED_NAME,

    /** UTF-8 인코딩 길이가 파일시스템 한도(255바이트)를 넘는다. */
    TOO_LONG,

    /** 같은 폴더에 종류가 다른 항목(파일↔폴더)이 같은 이름으로 있다 — 이름 변경으로 덮어쓸 수 없다. */
    EXISTS_AS_OTHER_TYPE
}
