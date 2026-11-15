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

/**
 * 문자열 리소스에서 새 폴더 기본 이름을 읽는 구현.
 * @param context 문자열 리소스 조회에 사용하는 애플리케이션 Context
 */
@Singleton
class ResourceOperationMessages @Inject constructor(
    @ApplicationContext private val context: Context
) : NewFolderNameDefaults {
    override val defaultNewFolderName: String get() = context.getString(R.string.default_new_folder_name)
}
