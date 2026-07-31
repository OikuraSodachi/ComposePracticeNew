package com.todokanai.composepracticenew.repository

import java.io.File
import java.util.zip.ZipFile

/** Contract for all file I/O operations. */
interface FileActionRepository {
    fun copyAction(files: Array<File>, currentPath: File)
    fun moveAction(files: Array<File>, currentPath: File)
    fun deleteAction(files: Array<File>)
    fun renameAction(selectedFile: File, name: String)
    fun newFolderAction(currentPath: File, folderName: String)
    fun zipAction(files: Array<File>, zipFileName: String)
    fun unzipAction(zipFile: ZipFile, currentPath: File, unzipHere: Boolean)
}
