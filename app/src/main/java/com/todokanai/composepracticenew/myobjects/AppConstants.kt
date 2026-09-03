package com.todokanai.composepracticenew.myobjects

/** app 레이어 전용 상수 모음. UI 렌더링·탐색 등 app 모듈 관심사만 정의한다. */
object AppConstants {

    /** AsyncImage로 표시할 파일 확장자 목록. 대소문자 무관 비교를 위해 lowercase 기준으로 정의한다. */
    val ASYNC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** 파일 탐색기 기본 브라우징 모드. */
    const val DEFAULT_MODE: Int = 10
    /** 체크박스 다중 선택이 활성화된 모드. */
    const val MULTI_SELECT_MODE: Int = 11
    /** 복사 목적지 탐색 후 확인하는 모드. */
    const val CONFIRM_MODE_COPY: Int = 12
    /** 이동 목적지 탐색 후 확인하는 모드. */
    const val CONFIRM_MODE_MOVE: Int = 13
    /** 압축 해제 목적지 탐색 후 확인하는 모드. */
    const val CONFIRM_MODE_UNZIP: Int = 14
    /** 현재 위치에 압축 해제 후 확인하는 모드. */
    const val CONFIRM_MODE_UNZIP_HERE: Int = 15
    /** 다운로드 목적지 탐색 후 확인하는 모드. */
    const val CONFIRM_MODE_DOWNLOAD: Int = 16
    /** 업로드 대상 선택 후 확인하는 모드. */
    const val CONFIRM_MODE_UPLOAD: Int = 17
}
