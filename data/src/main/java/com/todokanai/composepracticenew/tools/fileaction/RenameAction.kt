package com.todokanai.composepracticenew.tools.fileaction

import java.io.File

class RenameAction {
    fun renameAction(selectedFile: File, name: String) {
        selectedFile.renameTo(File("${selectedFile.parent}/$name"))
    }
}
