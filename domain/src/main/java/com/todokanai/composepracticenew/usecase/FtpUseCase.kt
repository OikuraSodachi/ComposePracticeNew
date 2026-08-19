package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.FtpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** FTP 연결 상태 조회·해제 및 파일 전송(다운로드·업로드) 작업을 도메인 계층에서 조율하는 UseCase. */
class FtpUseCase(private val repo: FtpRepository) {

    /** FTP 연결 또는 연결 해제 진행 중 여부를 방출한다. */
    val isConnecting: StateFlow<Boolean> = repo.isConnecting

    /** FTP 서버 연결을 해제한다. */
    suspend fun disconnect() = repo.disconnect()

    /**
     * remotePath의 파일을 localDestPath로 다운로드한다.
     * @param remotePath 다운로드할 원격 파일의 절대 경로
     * @param localDestPath 저장할 로컬 파일의 절대 경로 (디렉터리 경로 불가)
     */
    fun download(remotePath: String, localDestPath: String): Flow<ProgressState> =
        repo.download(remotePath, localDestPath)

    /**
     * localPath의 파일을 remoteDestPath에 업로드한다.
     * 로컬 파일 크기를 확인하고 FtpRepository.upload()를 호출한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remoteDestPath 저장될 원격 디렉터리의 절대 경로
     */
    fun upload(localPath: String, remoteDestPath: String): Flow<ProgressState> =
        repo.upload(localPath, remoteDestPath)

    /** path 파일을 같은 디렉터리 내에서 newName으로 이름 변경한다. 성공 여부를 반환한다. */
    suspend fun rename(path: String, newName: String): Boolean {
        val toPath = "${path.substringBeforeLast("/")}/$newName"
        return repo.rename(path, toPath)
    }

    /** path의 파일을 삭제한다. 성공 여부를 반환한다. */
    suspend fun deleteFile(path: String): Boolean = repo.deleteFile(path)

    /** path의 빈 디렉터리를 삭제한다. 성공 여부를 반환한다. */
    suspend fun removeDirectory(path: String): Boolean = repo.removeDirectory(path)

    /** path를 삭제한다. isDirectory에 따라 removeDirectory 또는 deleteFile을 호출한다. 성공 여부를 반환한다. */
    suspend fun delete(path: String, isDirectory: Boolean): Boolean =
        if (isDirectory) repo.removeDirectory(path) else repo.deleteFile(path)

    /** path에 새 디렉터리를 생성한다. 성공 여부를 반환한다. */
    suspend fun makeDirectory(path: String): Boolean = repo.makeDirectory(path)

    /** parentPath 아래에 dirName 이름의 디렉터리를 생성한다. 성공 여부를 반환한다. */
    suspend fun makeDirectory(parentPath: String, dirName: String): Boolean =
        repo.makeDirectory("$parentPath/$dirName")
}
