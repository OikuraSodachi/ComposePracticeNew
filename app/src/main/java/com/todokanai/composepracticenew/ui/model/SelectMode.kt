package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R

/**
 * 파일 목록 화면의 선택 모드.
 * 모드별 속성(확인 버튼 라벨 등)은 하위 타입이 직접 보유하고, 모드별 동작은 exhaustive `when`으로 분기해 새 모드 추가 시 누락이 컴파일 단계에서 드러난다.
 */
sealed interface SelectMode {

    /** 일반 탐색 모드. */
    data object Default : SelectMode

    /** 체크박스 다중 선택 모드. */
    data object MultiSelect : SelectMode

    /**
     * 목적지로 이동한 뒤 확인 버튼으로 작업을 실행하는 모드.
     * @param labelRes 확인 버튼에 표시할 문자열 리소스
     */
    sealed class Confirm(@StringRes val labelRes: Int) : SelectMode {
        /** 복사 목적지 탐색 후 확인하는 모드. */
        data object Copy : Confirm(R.string.btn_copy)
        /** 이동 목적지 탐색 후 확인하는 모드. */
        data object Move : Confirm(R.string.btn_move)
        /** 압축 해제 목적지 탐색 후 확인하는 모드. */
        data object Unzip : Confirm(R.string.btn_unzip)
        /** 현재 위치에 압축 해제 후 확인하는 모드. */
        data object UnzipHere : Confirm(R.string.btn_unzip_here)
        /** 다운로드 목적지 탐색 후 확인하는 모드. */
        data object Download : Confirm(R.string.btn_download)
        /** 업로드 대상 선택 후 확인하는 모드. */
        data object Upload : Confirm(R.string.btn_upload_confirm)
    }
}
