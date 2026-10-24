package com.todokanai.composepracticenew.myobjects

/** 파일 작업 알림 상수 모음. 작업 종류 식별은 [com.todokanai.composepracticenew.model.FileOperationType]이 담당한다. */
object OperationConstants {

    /** 알림 채널 식별자. */
    const val CHANNEL_ID: String = "Todokanai_FileManager"

    /** 알림 클릭 시 진행률 다이얼로그를 복원할 작업 종류의 id(FileOperationType.id)를 전달하는 Intent extra 키. */
    const val EXTRA_ACTION_KEY: String = "com.todokanai.composepracticenew.EXTRA_ACTION_KEY"

    /** 알림 클릭 시 진행률 다이얼로그를 띄울 작업 인스턴스 ID를 전달하는 Intent extra 키. */
    const val EXTRA_INSTANCE_ID: String = "com.todokanai.composepracticenew.EXTRA_INSTANCE_ID"
}
