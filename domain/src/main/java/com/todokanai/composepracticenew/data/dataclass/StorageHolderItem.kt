package com.todokanai.composepracticenew.data.dataclass

import java.io.File

data class StorageHolderItem(
    val storage: File,
    val absolutePath:String,
    val used:String,
    val total:String,
    val progress:Float
)
