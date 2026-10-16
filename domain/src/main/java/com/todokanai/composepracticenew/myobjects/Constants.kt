package com.todokanai.composepracticenew.myobjects

object Constants {

    const val BY_DEFAULT : String = "BY_DEFAULT"
    const val BY_NAME_ASCENDING : String = "BY_NAME_ASCENDING"
    const val BY_NAME_DESCENDING : String = "BY_NAME_DESCENDING"
    const val BY_SIZE_ASCENDING : String = "BY_SIZE_ASCENDING"
    const val BY_SIZE_DESCENDING : String = "BY_SIZE_DESCENDING"
    const val BY_TYPE_ASCENDING : String = "BY_TYPE_ASCENDING"
    const val BY_TYPE_DESCENDING : String = "BY_TYPE_DESCENDING"
    const val BY_DATE_ASCENDING : String = "BY_DATE_ASCENDING"
    const val BY_DATE_DESCENDING : String = "BY_DATE_DESCENDING"

    /** 원격 스토리지 파일명 인코딩 기본값. */
    const val REMOTE_ENCODING_DEFAULT = "UTF-8"
    /** 원격 스토리지 접속 정보 입력 시 선택 가능한 파일명 인코딩 목록. */
    val REMOTE_ENCODING_OPTIONS = listOf("UTF-8", "EUC-KR", "ISO-8859-1")

}