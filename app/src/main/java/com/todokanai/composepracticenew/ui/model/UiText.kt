package com.todokanai.composepracticenew.ui.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** 화면에 보일 문구. 문자열 리소스와 그 인자로 두어, Context가 있는 곳(화면·알림)에서 그때의 언어로 만든다. */
@Immutable
sealed interface UiText {

    /**
     * 문자열 리소스로 만드는 문구.
     * @param resId 문자열 리소스 id
     * @param args 리소스의 형식 인자. [UiText] 인자는 먼저 문구로 만든 뒤 넣는다
     */
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * 번역하지 않는 문구 그대로.
     * @param text 보일 문구
     */
    data class Raw(val text: String) : UiText

    /**
     * 문구를 만든다.
     * @param context 문자열 리소스를 읽을 Context
     * @return 보일 문구
     */
    fun resolve(context: Context): String = when (this) {
        is Raw -> text
        is Resource -> context.getString(resId, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }
}
