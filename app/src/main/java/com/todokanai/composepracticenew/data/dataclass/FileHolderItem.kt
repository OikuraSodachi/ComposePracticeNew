package com.todokanai.composepracticenew.data.dataclass

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

data class FileHolderItem(
    val file : File,
    val name:String,
    val size:String,
    val lastModified:String,
    val thumbnail:ImageBitmap
)


