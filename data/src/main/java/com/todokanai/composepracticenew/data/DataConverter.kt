package com.todokanai.composepracticenew.data

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DEFAULT
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_DESCENDING
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.fileexplorer.FileEntry
import java.io.File
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Converts raw File arrays into UI display models. */
@Singleton
class DataConverter @Inject constructor() {

    private fun File.toFileHolderItem(): FileHolderItem {
        val lastModified = DateFormat.getDateTimeInstance().format(this.lastModified())
        val size = if (this.isDirectory) {
            val subFiles = this.listFiles()
            if (subFiles == null) "null" else "${subFiles.size} 개"
        } else {
            readableFileSize_td(this.length())
        }
        return FileHolderItem(this.absolutePath, this.isDirectory, this.name, size, lastModified)
    }

    fun fileHolderItemList(files: List<FileEntry>, sortBy: String): List<FileHolderItem> {
        val fileArray = files.map { File(it.path) }.toTypedArray()
        return sortFiles(sortBy, fileArray).map { it.toFileHolderItem() }
    }

    private fun sortFiles(sortMode: String, files: Array<File>): List<File> {
        return when (sortMode) {
            BY_DEFAULT -> files.sortedWith(compareBy({ it.isFile }, { it.name }))
            BY_NAME_ASCENDING -> files.sortedBy { it.name }
            BY_NAME_DESCENDING -> files.sortedByDescending { it.name }
            BY_SIZE_ASCENDING -> {
                val sizes = files.associateWith { it.totalSize() }
                files.sortedBy { sizes[it] }
            }
            BY_SIZE_DESCENDING -> {
                val sizes = files.associateWith { it.totalSize() }
                files.sortedByDescending { sizes[it] }
            }
            BY_TYPE_ASCENDING -> files.sortedBy { it.extension }
            BY_TYPE_DESCENDING -> files.sortedByDescending { it.extension }
            BY_DATE_ASCENDING -> files.sortedBy { it.lastModified() }
            BY_DATE_DESCENDING -> files.sortedByDescending { it.lastModified() }
            else -> files.toList()
        }
    }

    /** 하위 디렉토리 포함한 크기 */
    private fun File.totalSize(): Long {
        if (this.isDirectory) {
            var size = 0L
            this.listFiles()?.forEach { file ->
                size += if (file.isDirectory) file.totalSize() else file.length()
            }
            return size
        }
        return this.length()
    }
}
