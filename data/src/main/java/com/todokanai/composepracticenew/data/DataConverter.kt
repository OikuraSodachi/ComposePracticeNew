package com.todokanai.composepracticenew.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.todokanai.composepracticenew.data.R
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Converts raw File arrays into UI display models. */
@Singleton
class DataConverter @Inject constructor(@ApplicationContext context: Context) {
    private val sorter = FileListSorter()
    private val thumbnailFolder =
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_folder_24)?.toBitmap()!!
    private val thumbnailPdf = ContextCompat.getDrawable(context, R.drawable.ic_pdf)?.toBitmap()!!
    private val thumbnailDefaultFile =
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_insert_drive_file_24)?.toBitmap()!!

    private fun thumbnail(file: File): Bitmap {
        return if (file.isDirectory) {
            thumbnailFolder
        } else {
            when (file.extension) {
                "pdf" -> thumbnailPdf
                else -> thumbnailDefaultFile
            }
        }
    }

    private fun File.toFileHolderItem(): FileHolderItem {
        val lastModified = DateFormat.getDateTimeInstance().format(this.lastModified())
        val size = if (this.isDirectory) {
            val subFiles = this.listFiles()
            if (subFiles == null) "null" else "${subFiles.size} 개"
        } else {
            readableFileSize_td(this.length())
        }
        return FileHolderItem(this, this.name, size, lastModified, thumbnail(this))
    }

    fun fileHolderItemList(files: Array<File>, sortBy: String): List<FileHolderItem> {
        val result = mutableListOf<FileHolderItem>()
        val sortedList = sorter.sortFileList(sortBy, files)
        sortedList.forEach { file ->
            result.add(file.toFileHolderItem())
        }
        return result
    }
}
