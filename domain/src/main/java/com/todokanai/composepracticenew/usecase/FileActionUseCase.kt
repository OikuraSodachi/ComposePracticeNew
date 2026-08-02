package com.todokanai.composepracticenew.usecase

class FileActionUseCase {

    /**
     * @param targetFiles absolutePath of files to compress
     * @param zipFile absolutePath of new zip file to create
     */
    suspend fun zipAction(targetFiles:List<String>,zipFile:String){

    }


    fun renameFile(targetFile:String, newName:String){

    }

    suspend fun deleteFile(targetFile:String){

    }

    suspend fun moveFile(targetFile:String, targetPath:String){

    }
}