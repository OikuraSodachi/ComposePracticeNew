package com.todokanai.composepracticenew.tools

import android.content.Context
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 새 폴더 다이얼로그에 미리 채울 기본 이름을 제공하는 계약. */
interface NewFolderNameDefaults {
    /** 새 폴더 다이얼로그에 미리 채울 기본 폴더 이름. */
    val defaultNewFolderName: String
}

/** 프로세스 보호를 받지 못해 작업을 시작하지 않았다는 거부 메시지를 제공하는 계약. */
interface ProtectionRefusedMessage {
    /** 시스템이 프로세스 보호를 거부해 작업을 시작하지 않았다는 메시지. */
    val protectionRefused: String
}

/**
 * 문자열 리소스에서 파일 작업 관련 문구를 읽는 구현. 쓰는 곳마다 필요한 계약으로만 바인딩한다.
 * @param context 문자열 리소스 조회에 사용하는 애플리케이션 Context
 */
@Singleton
class ResourceOperationMessages @Inject constructor(
    @ApplicationContext private val context: Context
) : NewFolderNameDefaults, ProtectionRefusedMessage {
    override val defaultNewFolderName: String get() = context.getString(R.string.default_new_folder_name)
    override val protectionRefused: String get() = context.getString(R.string.error_protection_refused)
}
