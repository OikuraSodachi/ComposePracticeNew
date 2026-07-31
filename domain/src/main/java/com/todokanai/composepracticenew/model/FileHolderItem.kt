package com.todokanai.composepracticenew.model

import android.graphics.Bitmap
import java.io.File

/** 파일 목록 UI 표시용 모델. */
data class FileHolderItem(
    val file: File,
    val name: String,
    val size: String,
    val lastModified: String,
    val thumbnail: Bitmap
)
