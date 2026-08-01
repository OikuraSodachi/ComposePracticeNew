package com.todokanai.composepracticenew.data

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.variables.FileListSorter
import com.todokanai.fileexplorer.FileEntry
import java.io.File
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Converts raw File arrays into UI display models. */
@Singleton
class DataConverter @Inject constructor() {
    private val sorter = FileListSorter()

    private fun File.toFileHolderItem(): FileHolderItem {
        val lastModified = DateFormat.getDateTimeInstance().format(this.lastModified())
        val size = if (this.isDirectory) {
            val subFiles = this.listFiles()
            if (subFiles == null) "null" else "${subFiles.size} 개"
        } else {
            readableFileSize_td(this.length())
        }
        return FileHolderItem(this, this.name, size, lastModified)
    }

    fun fileHolderItemList(files: List<FileEntry>, sortBy: String): List<FileHolderItem> {
        val fileArray = files.map { File(it.path) }.toTypedArray()
        val sortedList = sorter.sortFileList(sortBy, fileArray)
        return sortedList.map { it.toFileHolderItem() }
    }
}
