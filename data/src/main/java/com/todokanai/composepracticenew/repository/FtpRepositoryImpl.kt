package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FtpConnectionState
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** FtpRepository 계약을 FtpConnectionState에 위임해 구현한다. */
@Singleton
class FtpRepositoryImpl @Inject constructor(
    private val connectionState: FtpConnectionState
) : FtpRepository {

    /**
     * RemoteStorageItem의 접속 정보로 FTP 서버에 연결한다.
     * @param item 접속할 원격 스토리지의 호스트·포트·인증 정보
     */
    override suspend fun connect(item: RemoteStorageItem): Boolean =
        connectionState.connect(item.address, item.port, item.userId, item.password)

    /** FTP 서버 연결을 로그아웃 후 해제한다. */
    override suspend fun disconnect() = connectionState.disconnect()

    /**
     * 현재 FTP 작업 디렉터리의 절대 경로를 반환한다.
     * FTPClient.printWorkingDirectory()로 조회하며, 실패 시 빈 문자열을 반환한다.
     */
    override suspend fun getWorkingDirectory(): String =
        "" // stub — not yet implemented

    /**
     * path로 FTP 작업 디렉터리를 변경한다.
     * FTPClient.changeWorkingDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 이동할 원격 디렉터리의 절대 경로
     */
    override suspend fun changeDirectory(path: String): Boolean =
        false // stub — not yet implemented

    /**
     * path 디렉터리의 파일·하위 디렉터리 목록을 FileHolderItem 리스트로 반환한다.
     * FtpConnectionState.listFiles()로 얻은 FileEntry를 도메인 모델로 변환한다.
     * @param path 목록을 조회할 원격 디렉터리의 절대 경로
     */
    override suspend fun listFiles(path: String): List<FileHolderItem> =
        emptyList() // stub — not yet implemented

    /**
     * remotePath의 파일을 localPath로 다운로드한다.
     * @param remotePath 다운로드할 원격 파일의 절대 경로
     * @param localPath 저장할 로컬 파일의 절대 경로
     */
    override fun download(remotePath: String, localPath: String): Flow<ProgressState> =
        connectionState.download(remotePath, localPath)

    /**
     * localPath의 파일을 remotePath로 업로드한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remotePath 저장될 원격 파일의 절대 경로
     */
    override fun upload(localPath: String, remotePath: String): Flow<ProgressState> =
        connectionState.upload(localPath, remotePath)

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다.
     * FTPClient.rename()을 호출하며 성공 여부를 반환한다.
     * @param fromPath 원본 경로, @param toPath 변경할 경로
     */
    override suspend fun rename(fromPath: String, toPath: String): Boolean =
        false // stub — not yet implemented

    /**
     * path의 파일을 원격 서버에서 삭제한다.
     * FTPClient.deleteFile()을 호출하며 성공 여부를 반환한다.
     * @param path 삭제할 원격 파일의 절대 경로
     */
    override suspend fun deleteFile(path: String): Boolean =
        false // stub — not yet implemented

    /**
     * path에 새 원격 디렉터리를 생성한다.
     * FTPClient.makeDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 생성할 디렉터리의 절대 경로
     */
    override suspend fun makeDirectory(path: String): Boolean =
        false // stub — not yet implemented

    /**
     * path의 빈 디렉터리를 원격 서버에서 삭제한다.
     * FTPClient.removeDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 삭제할 빈 디렉터리의 절대 경로
     */
    override suspend fun removeDirectory(path: String): Boolean =
        false // stub — not yet implemented

    /**
     * path 파일의 크기를 바이트 단위로 반환한다.
     * FTPClient.mlistFile() 또는 SIZE 커맨드로 조회하며, 실패 시 -1을 반환한다.
     * @param path 크기를 조회할 원격 파일의 절대 경로
     */
    override suspend fun getFileSize(path: String): Long =
        -1L // stub — not yet implemented

    /**
     * path 파일의 최종 수정 시각을 ISO-8601 문자열로 반환한다.
     * FTPClient.getModificationTime()으로 조회하며, 실패 시 빈 문자열을 반환한다.
     * @param path 수정 시각을 조회할 원격 파일의 절대 경로
     */
    override suspend fun getModificationTime(path: String): String =
        "" // stub — not yet implemented
}
