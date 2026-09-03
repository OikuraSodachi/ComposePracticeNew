package com.todokanai.composepracticenew.myobjects

/** app 레이어 전용 상수 모음. UI 렌더링·탐색 등 app 모듈 관심사만 정의한다. */
object AppConstants {

    /** AsyncImage로 표시할 파일 확장자 목록. 대소문자 무관 비교를 위해 lowercase 기준으로 정의한다. */
    val ASYNC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
}
