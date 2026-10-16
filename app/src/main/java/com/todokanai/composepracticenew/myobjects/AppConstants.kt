package com.todokanai.composepracticenew.myobjects

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** app 레이어 전용 상수 모음. UI 렌더링 관심사만 정의한다. 선택 모드는 [com.todokanai.composepracticenew.ui.model.SelectMode]가 담당한다. */
object AppConstants {

    /** 파일 목록 썸네일 표시 크기. FileHolder 레이아웃 폭과 ImageHolder 디코딩 타깃이 이 값을 공유한다. */
    val THUMBNAIL_SIZE_DP: Dp = 50.dp

    /** AsyncImage로 표시할 파일 확장자 목록. 대소문자 무관 비교를 위해 lowercase 기준으로 정의한다. */
    val ASYNC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
}
