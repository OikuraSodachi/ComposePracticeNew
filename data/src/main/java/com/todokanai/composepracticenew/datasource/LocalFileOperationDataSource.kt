package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 로컬 파일 시스템에서 파일 뮤테이션(복사·이동·삭제·압축·압축 해제·폴더 생성)을 수행하는 DataSource 인터페이스.
 * FileActionRepositoryImpl이 이 인터페이스를 통해 로컬 I/O에 간접 의존하므로, 구현체 교체로 스토리지 백엔드를 교체할 수 있다.
 */
interface LocalFileOperationDataSource {
    /**
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param zipFile 생성할 ZIP 파일의 절대 경로. 이미 있으면 덮어쓰지 않고 `이름 (n).zip` 형태의 새 이름으로 만든다
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit)
    /**
     * @param targetFiles 복사할 파일 경로 집합
     * @param targetPath 복사 대상 디렉터리 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit)
    /**
     * @param targetFile 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 이름
     * @param overwrite 같은 이름의 항목이 있을 때 덮어쓸지 여부. false면 실패한다. true면 파일은 교체하고 폴더는 합친다(종류가 다르면 실패)
     */
    suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean)
    /**
     * @param targetFile 삭제할 파일 또는 디렉터리의 절대 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit)
    /**
     * @param targetFiles 이동할 파일 경로 집합
     * @param targetPath 이동 대상 디렉터리 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit)
    /**
     * @param zipFiles 압축 해제할 ZIP 파일 경로 집합
     * @param destPath 압축 해제 대상 디렉터리 경로
     * @param unzipHere true이면 각 ZIP과 같은 디렉터리에 해제, false이면 destPath 사용
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit)
    /**
     * @param parentPath 새 폴더를 생성할 부모 디렉터리 경로
     * @param name 생성할 폴더 이름
     */
    suspend fun makeDirectory(parentPath: String, name: String)
}
