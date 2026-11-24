package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchiveContents
import com.todokanai.composepracticenew.model.IoResult

/** 로컬 압축 파일의 항목 목록을 내용을 풀지 않고 읽는다. 형식과 분할 압축의 이름 규칙은 압축 해제와 같다. */
interface ArchiveContentRepository {

    /**
     * [path]가 내용을 볼 수 있는 압축 파일인지 이름으로만 판정한다(파일을 읽지 않는다).
     * @param path 파일 경로
     * @return 지원하는 형식의 단일 아카이브이거나 분할 압축의 시작 볼륨이면 true. 분할 압축의 다른 볼륨은 false
     */
    fun canView(path: String): Boolean

    /**
     * [archivePath]의 항목 목록을 읽어 폴더 트리로 돌려준다.
     * 항목 목록까지 암호화한 압축 파일은 [askPassword]로 비밀번호를 묻고, 틀리면 다시 묻는다. 내용만 암호화한 항목은 묻지 않는다.
     * @param archivePath 압축 파일(분할 압축이면 시작 볼륨) 경로
     * @param askPassword 비밀번호를 묻는 통로. 사용자가 취소하면 CancellationException을 던진다
     * @return 루트 폴더와 그 목록의 식별값. 읽지 못하면(지원하지 않는 이름, 손상, 빠진 볼륨, 해석하지 못한 이름) 실패
     */
    suspend fun contentsOf(archivePath: String, askPassword: ArchivePasswordProvider): IoResult<ArchiveContents>
}
