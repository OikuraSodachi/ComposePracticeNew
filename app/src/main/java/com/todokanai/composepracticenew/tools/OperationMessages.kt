package com.todokanai.composepracticenew.tools

import android.content.Context
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 파일 작업 완료·실행 거부 알림 메시지 문자열을 제공하는 계약. */
interface OperationMessages {
    /** 일반 작업 완료 메시지. */
    val notiComplete: String
    /** 이동 완료 메시지. */
    val notiMoveComplete: String
    /** 삭제 완료 메시지. */
    val notiDeleteComplete: String
    /** 다운로드 완료 메시지. */
    val notiDownloadComplete: String
    /** 업로드 완료 메시지. */
    val notiUploadComplete: String
    /** 대상 폴더의 목록을 확인하지 못해 이름 충돌을 판정할 수 없어 작업을 실행하지 않았다는 메시지. */
    val destinationUnverified: String
    /** 새 폴더 다이얼로그에 미리 채울 기본 폴더 이름. */
    val defaultNewFolderName: String
}

/**
 * 문자열 리소스에서 메시지를 읽는 [OperationMessages] 구현.
 * @param context 문자열 리소스 조회에 사용하는 애플리케이션 Context
 */
@Singleton
class CompletionMessageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : OperationMessages {
    override val notiComplete: String get() = context.getString(R.string.noti_complete)
    override val notiMoveComplete: String get() = context.getString(R.string.noti_move_complete)
    override val notiDeleteComplete: String get() = context.getString(R.string.noti_delete_complete)
    override val notiDownloadComplete: String get() = context.getString(R.string.noti_download_complete)
    override val notiUploadComplete: String get() = context.getString(R.string.noti_upload_complete)
    override val destinationUnverified: String get() = context.getString(R.string.error_destination_unverified)
    override val defaultNewFolderName: String get() = context.getString(R.string.default_new_folder_name)
}
