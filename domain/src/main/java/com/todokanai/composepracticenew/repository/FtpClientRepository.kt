package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry

/** FTP 서버 연결 및 파일 시스템 접근에 대한 계약. */
interface FtpClientRepository {
    suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean
    suspend fun listFiles(path: String): List<FileEntry>
    fun isRemotePath(path: String): Boolean
    fun getParent(path: String): String?
    fun buildRootPath(address: String): String
}
