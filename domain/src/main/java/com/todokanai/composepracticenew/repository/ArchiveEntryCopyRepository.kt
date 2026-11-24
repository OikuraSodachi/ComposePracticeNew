package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase

/**
 * 압축 파일 안에서 고른 항목만 로컬 폴더로 푼다. 형식·비밀번호·분할 압축·충돌 처리는 압축 해제([FileActionRepository.unzipAction])와 같다.
 * 작업이 취소되면 onProgress가 CancellationException을 던진다. 구현은 그것을 잡아 결과로 바꾸지 않고, 쓰다 만 파일을 지운 뒤 전파한다(이미 다 쓴 파일은 남긴다).
 */
interface ArchiveEntryCopyRepository {

    /**
     * [selection]의 항목을 [destPath] 안에, 고른 폴더 기준의 상대 위치로 푼다. 폴더를 골랐으면 그 아래 항목 전체를 푼다.
     * 폴더는 이미 있는 폴더에 묻지 않고 합치고, 같은 이름의 파일 자리는 [resolveConflict]에 묻는다(PROCEED는 파일을 덮어쓴다). 종류가 다른 자리는 건너뛰기만 할 수 있다.
     * 암호화된 항목은 고른 항목 안에서 비밀번호를 확인한다 — 고른 항목에 암호화된 것이 없으면 묻지 않는다.
     * @param selection 고른 항목. 비어 있지 않아야 한다
     * @param destPath 풀 대상 디렉터리 경로
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param askPassword 암호화된 항목의 비밀번호를 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 결과. 항목을 고른 뒤 압축 파일의 항목 목록이 바뀌었으면 아무것도 풀지 않고 실패한다.
     * 풀 수 없는 경로·링크 항목은 건너뛰고 그 항목들을 담은 [IoResult.PartiallyFailed]
     */
    suspend fun extractEntries(
        selection: ArchiveEntrySelection,
        destPath: String,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit = {}
    ): IoResult<Unit>
}
