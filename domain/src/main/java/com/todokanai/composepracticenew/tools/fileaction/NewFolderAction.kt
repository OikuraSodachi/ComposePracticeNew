package com.todokanai.composepracticenew.tools.fileaction

import java.io.File

/** ViewModel 이상 단계에서 보이면 안됨 */
class NewFolderAction {


    fun newFolderAction(path: File, folderName:String){
        File(path.absolutePath, folderName).mkdir()
    }       // Done
}