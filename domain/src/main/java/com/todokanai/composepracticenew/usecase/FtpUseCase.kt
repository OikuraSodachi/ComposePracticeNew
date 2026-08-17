package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.FtpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** FTP 파일 전송(다운로드·업로드) 작업을 도메인 계층에서 조율하는 UseCase. */
class FtpUseCase(private val repo: FtpRepository) {

    /**
     * remotePath의 파일을 localDestPath로 다운로드한다.
     * 원격 파일 크기를 먼저 조회해 로컬 여유 공간을 검증한 뒤 FtpRepository.download()를 호출한다.
     * @param remotePath 다운로드할 원격 파일의 절대 경로
     * @param localDestPath 저장할 로컬 디렉터리 또는 파일의 절대 경로
     */
    fun download(remotePath: String, localDestPath: String): Flow<ProgressState> = flow {
        // stub — not yet implemented
    }

    /**
     * localPath의 파일을 remoteDestPath에 업로드한다.
     * 로컬 파일 크기를 확인하고 FtpRepository.upload()를 호출한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remoteDestPath 저장될 원격 디렉터리의 절대 경로
     */
    fun upload(localPath: String, remoteDestPath: String): Flow<ProgressState> = flow {
        // stub — not yet implemented
    }
}
