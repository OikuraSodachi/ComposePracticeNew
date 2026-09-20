package com.todokanai.composepracticenew.myobjects

/** 파일 작업 식별 키·알림 상수 모음. */
object OperationConstants {

    /** 알림 채널 식별자. */
    const val CHANNEL_ID: String = "Todokanai_FileManager"

    /** 알림 클릭 시 진행률 다이얼로그를 복원할 작업 키를 전달하는 Intent extra 키. */
    const val EXTRA_ACTION_KEY: String = "com.todokanai.composepracticenew.EXTRA_ACTION_KEY"

    /** 복사 작업 식별 키. */
    const val ACTION_KEY_COPY: Int = 20
    /** 이동 작업 식별 키. */
    const val ACTION_KEY_MOVE: Int = 21
    /** 삭제 작업 식별 키. */
    const val ACTION_KEY_DELETE: Int = 22
    /** 압축 작업 식별 키. */
    const val ACTION_KEY_ZIP: Int = 23
    /** 압축 해제 작업 식별 키. */
    const val ACTION_KEY_UNZIP: Int = 24
    /** 다운로드 작업 식별 키. */
    const val ACTION_KEY_DOWNLOAD: Int = 25
    /** 업로드 작업 식별 키. */
    const val ACTION_KEY_UPLOAD: Int = 26

    /** actionKey가 원격(다운로드·업로드) 작업인지 판별한다. */
    fun isRemoteOperation(actionKey: Int?) =
        actionKey == ACTION_KEY_DOWNLOAD || actionKey == ACTION_KEY_UPLOAD
}
