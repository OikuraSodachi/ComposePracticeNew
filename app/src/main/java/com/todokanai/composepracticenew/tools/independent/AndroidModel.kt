package com.todokanai.composepracticenew.tools.independent

/**
 * 독립적으로 사용 가능하고, Android 의존성 있는 method 모음
 */
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Application 종료 method
 * Service가 실행중일 경우 서비스를 실행한 Intent도 입력할것
 */
fun exit_td(activity: Activity, serviceIntent: Intent? = null) {
    serviceIntent?.let { activity.stopService(it) }
    activity.finishAndRemoveTask()
}

suspend fun getThumbnail_td(file: File,width:Int = 100,height:Int = 100): Bitmap = withContext(Dispatchers.IO){
    ThumbnailUtils.extractThumbnail(
    BitmapFactory.decodeFile(file.absolutePath), width, height)
}