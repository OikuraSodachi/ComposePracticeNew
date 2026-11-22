package com.todokanai.composepracticenew.tools.independent

// 이 파일의 함수들은 독립적으로 쓸 수 있지만 Android 프레임워크에 의존한다.
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