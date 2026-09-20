package com.todokanai.composepracticenew.tools

import android.content.Context
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 파일 작업 완료 알림 메시지 문자열을 제공한다. */
@Singleton
class CompletionMessageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val notiComplete: String get() = context.getString(R.string.noti_complete)
    val notiMoveComplete: String get() = context.getString(R.string.noti_move_complete)
    val notiDeleteComplete: String get() = context.getString(R.string.noti_delete_complete)
    val notiDownloadComplete: String get() = context.getString(R.string.noti_download_complete)
    val notiUploadComplete: String get() = context.getString(R.string.noti_upload_complete)
}
