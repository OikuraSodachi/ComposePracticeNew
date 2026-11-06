package com.todokanai.composepracticenew.myobjects

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** app 레이어 전용 상수 모음. UI 렌더링 관심사만 정의한다. 선택 모드는 [com.todokanai.composepracticenew.ui.model.SelectMode]가 담당한다. */
object AppConstants {

    /** 파일 목록 썸네일 표시 크기. FileHolder 레이아웃 폭과 ImageHolder 디코딩 타깃이 이 값을 공유한다. */
    val THUMBNAIL_SIZE_DP: Dp = 50.dp

    /** 원격 스토리지 접속 정보 입력 시 선택 가능한 파일명 인코딩 목록. 기본값([Constants.REMOTE_ENCODING_DEFAULT])을 언제나 고를 수 있도록 맨 앞에 둔다. */
    val REMOTE_ENCODING_OPTIONS: List<String> = listOf(Constants.REMOTE_ENCODING_DEFAULT, "EUC-KR", "ISO-8859-1")
}
