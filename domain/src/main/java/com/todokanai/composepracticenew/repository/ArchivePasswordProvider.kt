package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt

/**
 * 압축 해제 중 암호화된 항목을 만난 구현이 비밀번호를 묻는 통로. 구현은 비밀번호가 필요할 때만 묻는다.
 * 답을 기다리는 동안 작업이 멈춘다. 사용자가 작업을 취소했거나 답이 오지 않으면 CancellationException을 던지며, 구현은 그것을 잡지 않고 전파한다.
 */
fun interface ArchivePasswordProvider {
    /**
     * [archiveName]의 비밀번호를 받는다.
     * @param archiveName 비밀번호가 필요한 압축 파일의 이름
     * @param prompt 묻는 이유(처음·틀림·틀렸거나 손상)
     * @return 입력한 비밀번호(비어 있지 않다)
     */
    suspend fun passwordFor(archiveName: String, prompt: ArchivePasswordPrompt): String
}
