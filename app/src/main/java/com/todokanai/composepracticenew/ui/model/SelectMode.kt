package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R

/**
 * 파일 목록 화면의 선택 모드.
 * 화면별로 가능한 모드는 [LocalSelectMode] / [RemoteSelectMode]로, 확인 버튼을 갖는 모드는 [ConfirmMode]로 타입을 좁혀
 * 해당 화면·함수가 처리할 수 없는 모드 조합을 컴파일 단계에서 배제한다.
 */
sealed interface SelectMode {

    /** 일반 탐색 모드. 두 화면 공통. */
    data object Default : LocalSelectMode, RemoteSelectMode

    /** 체크박스 다중 선택 모드. 두 화면 공통. */
    data object MultiSelect : LocalSelectMode, RemoteSelectMode

    /**
     * 로컬 화면에서 목적지로 이동한 뒤 확인 버튼으로 실행하는 로컬 파일 작업 모드. 실행 전 이름 충돌을 검사한다.
     * @param labelRes 확인 버튼에 표시할 문자열 리소스
     */
    sealed class LocalOperation(@StringRes override val labelRes: Int) : LocalSelectMode, ConfirmMode {
        /** 복사 목적지 탐색 후 확인하는 모드. */
        data object Copy : LocalOperation(R.string.btn_copy)
        /** 이동 목적지 탐색 후 확인하는 모드. */
        data object Move : LocalOperation(R.string.btn_move)
        /** 압축 해제 목적지 탐색 후 확인하는 모드. */
        data object Unzip : LocalOperation(R.string.btn_unzip)
        /** 현재 위치에 압축 해제 후 확인하는 모드. */
        data object UnzipHere : LocalOperation(R.string.btn_unzip_here)
    }

    /** 로컬 화면에서 다운로드 목적지를 탐색한 뒤 확인하는 모드. */
    data object Download : LocalSelectMode, ConfirmMode {
        @StringRes override val labelRes: Int = R.string.btn_download
    }

    /** 원격 화면에서 업로드 목적지를 탐색한 뒤 확인하는 모드. */
    data object Upload : RemoteSelectMode, ConfirmMode {
        @StringRes override val labelRes: Int = R.string.btn_upload_confirm
    }
}

/** 로컬 파일 목록 화면에서 가능한 선택 모드. */
sealed interface LocalSelectMode : SelectMode

/** 원격 파일 목록 화면에서 가능한 선택 모드. */
sealed interface RemoteSelectMode : SelectMode

/** 확인 버튼을 표시하는 선택 모드. */
sealed interface ConfirmMode : SelectMode {
    /** 확인 버튼에 표시할 문자열 리소스. */
    @get:StringRes
    val labelRes: Int
}
