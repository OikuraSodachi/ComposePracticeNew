package com.todokanai.composepracticenew.tools.independent

/**
 * 독립적으로 사용 가능하고, Android 의존성 있는 method 모음
 */
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun getThumbnail_td(file: File,width:Int = 100,height:Int = 100): Bitmap = withContext(Dispatchers.IO){
    ThumbnailUtils.extractThumbnail(
    BitmapFactory.decodeFile(file.absolutePath), width, height)
}