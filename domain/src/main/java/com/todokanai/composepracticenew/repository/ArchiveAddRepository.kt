package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase

/**
 * 로컬 파일·폴더를 기존 압축 파일 안의 폴더에 넣는다. 압축 파일은 끝까지 쓴 사본으로 한 번에 교체해, 실패·취소하면 원래 압축 파일이 그대로 남는다.
 * 작업이 취소되면 onProgress가 CancellationException을 던진다. 구현은 그것을 잡아 결과로 바꾸지 않고, 쓰던 사본을 지운 뒤 전파한다.
 */
interface ArchiveAddRepository {

    /**
     * [archivePath]의 압축 파일에 항목을 추가할 수 있는 형식인지 이름으로 판정한다. 이름으로 알 수 없는 제약(이름이 같은 분할 압축)은 [addFiles]가 실행할 때 거부한다.
     * @param archivePath 압축 파일 경로
     * @return 추가할 수 있는 형식의 이름이면 true
     */
    fun canAddTo(archivePath: String): Boolean

    /**
     * [sources]를 [archivePath] 압축 파일 안의 [folderNames] 폴더에 넣는다. 폴더를 고르면 그 아래 항목 전체를 넣는다.
     * 폴더는 이미 있는 폴더에 묻지 않고 합치고, 같은 이름의 파일은 [resolveConflict]에 묻는다(PROCEED는 기존 항목을 새 항목으로 바꾼다). 종류가 다른 자리는 건너뛰기만 할 수 있다.
     * 압축 파일에 암호화된 항목이 있으면 비밀번호를 묻고 그 항목으로 확인한 뒤, 새 파일 항목도 같은 비밀번호로 암호화한다.
     * @param sources 넣을 로컬 파일·폴더 경로. 비어 있지 않아야 한다
     * @param archivePath 항목을 넣을 압축 파일 경로
     * @param folderNames 압축 파일 루트부터 넣을 폴더까지의 폴더 이름들. 비었으면 루트
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param askPassword 암호화된 압축 파일의 비밀번호를 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 결과. 추가할 수 없는 압축 파일이거나 [sources]에 그 압축 파일 자신이 들어 있으면 압축 파일을 바꾸지 않고 실패한다.
     * 넣지 못한 항목(링크, 안을 읽지 못한 폴더)이 있으면 나머지를 넣고 그 항목들을 담은 [IoResult.PartiallyFailed]
     */
    suspend fun addFiles(
        sources: Set<String>,
        archivePath: String,
        folderNames: List<String>,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit = {}
    ): IoResult<Unit>
}
