package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry
import com.todokanai.composepracticenew.data.ftp.FtpConnectionState
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** FtpRepository 계약을 FtpConnectionState에 위임해 구현한다. */
@Singleton
class FtpRepositoryImpl @Inject constructor(
    private val connectionState: FtpConnectionState
) : FtpRepository {

    /** FTP 연결 또는 연결 해제 진행 중 여부를 방출한다. */
    override val isConnecting: StateFlow<Boolean> = connectionState.isConnecting

    /** FTP 서버에 연결 및 로그인이 완료된 경우 true를 방출한다. */
    override val isConnected: StateFlow<Boolean> = connectionState.isConnected

    /**
     * address, port, userId, password, encoding으로 FTP 서버에 연결한다.
     * @param address 호스트 주소, @param port 포트 번호, @param userId 사용자 ID, @param password 비밀번호, @param encoding 컨트롤 채널 문자 인코딩
     */
    override suspend fun connect(address: String, port: Int, userId: String, password: String, encoding: String): Boolean =
        connectionState.connect(address, port, userId, password, encoding)

    /** FTP 서버 연결을 로그아웃 후 해제한다. */
    override suspend fun disconnect() { connectionState.disconnect() }

    /** path가 "ftp://" 스킴으로 시작하는 원격 경로인지 판별한다. */
    override fun isRemotePath(path: String): Boolean = path.startsWith("ftp://")

    /**
     * 현재 FTP 작업 디렉터리의 절대 경로를 반환한다.
     * FTPClient.printWorkingDirectory()로 조회하며, 실패 시 빈 문자열을 반환한다.
     */
    override suspend fun getWorkingDirectory(): String = connectionState.getWorkingDirectory()

    /**
     * path로 FTP 작업 디렉터리를 변경한다.
     * FTPClient.changeWorkingDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 이동할 원격 디렉터리의 절대 경로
     */
    override suspend fun changeDirectory(path: String): Boolean = connectionState.changeDirectory(path)

    /**
     * path 디렉터리의 파일·하위 디렉터리 목록을 FileEntry 리스트로 반환한다.
     * @param path 목록을 조회할 원격 디렉터리의 절대 경로
     */
    override suspend fun listFiles(path: String): List<FileEntry> =
        connectionState.listFiles(path)

    /**
     * remotePath의 파일 또는 디렉터리를 localPath로 다운로드한다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     */
    override fun download(remotePath: String, localPath: String, isDirectory: Boolean): Flow<ProgressState> =
        connectionState.download(remotePath, localPath, isDirectory)

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
    override suspend fun rename(fromPath: String, toPath: String): Boolean = connectionState.rename(fromPath, toPath)

    /**
     * path의 파일을 원격 서버에서 삭제한다.
     * FTPClient.deleteFile()을 호출하며 성공 여부를 반환한다.
     * @param path 삭제할 원격 파일의 절대 경로
     */
    override suspend fun deleteFile(path: String): Boolean = connectionState.deleteFile(path)

    /**
     * path에 새 원격 디렉터리를 생성한다.
     * FTPClient.makeDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 생성할 디렉터리의 절대 경로
     */
    override suspend fun makeDirectory(path: String): Boolean = connectionState.makeDirectory(path)

    /**
     * path의 빈 디렉터리를 원격 서버에서 삭제한다.
     * FTPClient.removeDirectory()를 호출하며 성공 여부를 반환한다.
     * @param path 삭제할 빈 디렉터리의 절대 경로
     */
    override suspend fun removeDirectory(path: String): Boolean = connectionState.removeDirectory(path)

    /**
     * path 파일의 크기를 바이트 단위로 반환한다.
     * FTPClient.mlistFile()로 조회하며, 실패 시 -1을 반환한다.
     * @param path 크기를 조회할 원격 파일의 절대 경로
     */
    override suspend fun getFileSize(path: String): Long = connectionState.getFileSize(path)

    /**
     * path 파일의 최종 수정 시각을 FTP MDTM 형식 문자열로 반환한다.
     * FTPClient.getModificationTime()으로 조회하며, 실패 시 빈 문자열을 반환한다.
     * @param path 수정 시각을 조회할 원격 파일의 절대 경로
     */
    override suspend fun getModificationTime(path: String): String = connectionState.getModificationTime(path)
}
