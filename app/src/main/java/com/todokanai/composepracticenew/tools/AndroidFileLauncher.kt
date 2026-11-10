package com.todokanai.composepracticenew.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.todokanai.composepracticenew.model.FileLaunchResult
import com.todokanai.composepracticenew.repository.FileLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱의 FileProvider로 만든 content URI를 ACTION_VIEW로 넘겨 [FileLauncher]를 구현한다.
 * 형식은 확장자로만 정하며, 알 수 없으면 열지 않는다. 여는 앱이 있는지는 미리 묻지 않고 시작을 시도해 판단한다 — 미리 물으려면 패키지 공개 범위(`<queries>`) 선언이 필요하고, 시작 자체는 공개 범위와 무관하다.
 * @param context FileProvider URI 발급과 Activity 시작에 쓰는 애플리케이션 Context
 */
@Singleton
class AndroidFileLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : FileLauncher {

    override fun launch(path: String): FileLaunchResult {
        val file = File(path)
        val mimeType = mimeTypeOf(file) ?: return FileLaunchResult.UnknownType
        // 애플리케이션 Context에서 시작하므로 새 task가 필요하다
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.provider", file), mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            FileLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            FileLaunchResult.NoHandler
        }
    }

    /** file의 확장자로 정한 MIME 형식. 확장자가 없거나 등록되지 않았으면 null. */
    private fun mimeTypeOf(file: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
}
