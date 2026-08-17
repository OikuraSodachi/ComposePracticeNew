package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow

/** FTP 서버와의 연결, 탐색, 파일 전송, 파일 조작 작업의 전체 계약. */
interface FtpRepository {

    // region Connection

    /** RemoteStorageItem의 접속 정보를 사용해 FTP 서버에 연결하고 로그인한다. 성공 여부를 반환한다. */
    suspend fun connect(item: RemoteStorageItem): Boolean

    /** address, port, userId, password로 FTP 서버에 직접 연결한다. 성공 여부를 반환한다. */
    suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean

    /** FTP 서버와의 연결을 종료한다. */
    suspend fun disconnect()

    // endregion

    // region Navigation

    /** 현재 작업 디렉토리의 절대 경로를 반환한다. */
    suspend fun getWorkingDirectory(): String

    /** path로 작업 디렉토리를 변경한다. 성공 여부를 반환한다. */
    suspend fun changeDirectory(path: String): Boolean

    // endregion

    // region Listing

    /** path 디렉토리의 파일 및 하위 디렉토리 목록을 FileEntry 리스트로 반환한다. */
    suspend fun listFiles(path: String): List<FileEntry>

    // endregion

    // region Routing

    /** path가 이 FTP 저장소가 처리해야 할 원격 경로인지 판별한다. */
    fun isRemotePath(path: String): Boolean

    // endregion

    // region Transfer

    /**
     * remotePath의 파일을 localPath로 다운로드한다.
     * @param remotePath 다운로드할 원격 파일의 절대 경로
     * @param localPath 저장할 로컬 파일의 절대 경로
     * @returns 전송 진행 상태를 방출하는 Flow
     */
    fun download(remotePath: String, localPath: String): Flow<ProgressState>

    /**
     * localPath의 파일을 remotePath로 업로드한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remotePath 저장될 원격 파일의 절대 경로
     * @returns 전송 진행 상태를 방출하는 Flow
     */
    fun upload(localPath: String, remotePath: String): Flow<ProgressState>

    // endregion

    // region File Operations

    /** fromPath의 파일 또는 디렉토리를 toPath로 이름 변경 또는 이동한다. 성공 여부를 반환한다. */
    suspend fun rename(fromPath: String, toPath: String): Boolean

    /** path의 파일을 삭제한다. 성공 여부를 반환한다. */
    suspend fun deleteFile(path: String): Boolean

    /** path에 새 디렉토리를 생성한다. 성공 여부를 반환한다. */
    suspend fun makeDirectory(path: String): Boolean

    /** path의 빈 디렉토리를 삭제한다. 성공 여부를 반환한다. */
    suspend fun removeDirectory(path: String): Boolean

    // endregion

    // region File Info

    /** path 파일의 크기를 바이트 단위로 반환한다. 조회 실패 시 -1을 반환한다. */
    suspend fun getFileSize(path: String): Long

    /** path 파일의 최종 수정 시각을 ISO-8601 문자열로 반환한다. 조회 실패 시 빈 문자열을 반환한다. */
    suspend fun getModificationTime(path: String): String

    // endregion
}
