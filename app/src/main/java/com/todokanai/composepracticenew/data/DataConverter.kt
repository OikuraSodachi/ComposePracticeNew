package com.todokanai.composepracticenew.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.data.dataclass.FileHolderItem
import com.todokanai.composepracticenew.data.dataclass.StorageHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataConverter @Inject constructor(@ApplicationContext context: Context) {
    private val sorter = FileListSorter()
    private val thumbnailFolder =
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_folder_24)?.toBitmap()!!
    private val thumbnailPdf = ContextCompat.getDrawable(context, R.drawable.ic_pdf)?.toBitmap()!!
    private val thumbnailDefaultFile =
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_insert_drive_file_24)
            ?.toBitmap()!!

    private fun thumbnail(file: File): Bitmap {
        return if (file.isDirectory) {
            thumbnailFolder
        } else {
            when (file.extension) {
                "pdf" -> { thumbnailPdf }
                else -> { thumbnailDefaultFile }
            }
        }
    }

    private fun File.toFileHolderItem(): FileHolderItem {
        val file = this
        val lastModified = DateFormat.getDateTimeInstance().format(file.lastModified())
        val size =
            if(file.isDirectory) {
                val subFiles = file.listFiles()
                if(subFiles == null){
                    "null"
                }else {
                    "${subFiles.size} 개"
                }
            } else {
                readableFileSize_td(file.length())
            }
        val thumbnail = thumbnail(file)

        return FileHolderItem(file, file.name, size, lastModified, thumbnail.asImageBitmap())
    }

    fun fileHolderItemList(files: Array<File>, sortBy: String): List<FileHolderItem> {
        val result = mutableListOf<FileHolderItem>()
        val sortedFileHolderItemList = sorter.sortFileList(sortBy, files)
        sortedFileHolderItemList.forEach { file ->
            result.add(file.toFileHolderItem())
        }
        return result
    }

    private fun File.toStorageHolderItem(): StorageHolderItem {
        val file = this
        val storageSize = file.totalSpace
        val freeSize = file.freeSpace
        val progress = ((storageSize.toDouble() - freeSize.toDouble()) / storageSize.toDouble()).toFloat()
        val used = readableFileSize_td(storageSize - freeSize)
        val total = readableFileSize_td(storageSize)
        return StorageHolderItem(file, file.absolutePath, used, total, progress)
    }

    fun storageHolderItemList(storages: List<File>): List<StorageHolderItem> {
        val result = mutableListOf<StorageHolderItem>()
        storages.forEach {
            result.add(it.toStorageHolderItem())
        }
        return result
    }
}
