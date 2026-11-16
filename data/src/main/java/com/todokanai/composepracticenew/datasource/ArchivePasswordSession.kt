package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt

/**
 * 압축 파일 하나의 비밀번호를 필요할 때 묻고, 내용으로 확인한 비밀번호를 그 압축 파일 안에서 다시 쓴다. 압축 파일마다 새로 만든다 —
 * 다른 압축 파일에 앞 비밀번호를 쓰지 않도록.
 * 비밀번호는 출력을 만들기 전에 확인한다([verifiedPassword]의 check) — 틀린 비밀번호로 쓴 파일을 되돌릴 일이 없도록.
 * 형식별 추출기([ArchiveExtractor])가 같은 규칙으로 묻고 다시 묻도록 한 곳에 둔다.
 * @param ask 비밀번호를 묻는 함수. 추출 라이브러리의 콜백 안에서도 부르므로 일시 중단하지 않고 답을 기다린다. 사용자가 취소했거나 답이 없으면 CancellationException을 던진다
 * @param ensureActive 비밀번호를 확인하려고 내용을 읽는 동안 작업 취소를 확인하는 함수. 취소됐으면 CancellationException을 던진다 — 확인 읽기는 진행을 보고하지 않아 진행 보고가 취소를 확인하지 못하므로
 */
class ArchivePasswordSession(
    private val ask: (ArchivePasswordPrompt) -> String,
    val ensureActive: () -> Unit,
) {

    /** 이 압축 파일에서 내용으로 확인한 비밀번호. 아직 없으면 null. */
    var verified: String? = null
        private set

    /**
     * 확인된 비밀번호를 돌려준다. 없으면 묻고, [check]가 받아들일 때까지 결과에 맞는 이유로 다시 묻는다.
     * 다시 묻기는 사용자가 취소하거나 답이 없을 때([ask]가 던지는 CancellationException)에만 끝난다.
     * @param check 후보 비밀번호로 출력을 만들지 않고 내용을 풀어 보는 함수
     * @return 확인된 비밀번호
     */
    fun verifiedPassword(check: (String) -> PasswordCheck): String {
        verified?.let { return it }
        var prompt = ArchivePasswordPrompt.FIRST
        while (true) {
            val candidate = ask(prompt)
            prompt = when (check(candidate)) {
                PasswordCheck.ACCEPTED -> return candidate.also { verified = it }
                PasswordCheck.WRONG -> ArchivePasswordPrompt.WRONG
                PasswordCheck.WRONG_OR_DAMAGED -> ArchivePasswordPrompt.WRONG_OR_DAMAGED
            }
        }
    }
}

/**
 * 비밀번호 확인으로 끝까지 풀어 볼 항목을 고른다. 형식별 추출기가 같은 기준으로 고르도록 한 곳에 둔다.
 * 내용이 있는 항목 중 가장 작은 것을 고른다 — 0바이트 항목은 풀 내용이 없어 검증값이 없는 형식(RAR4)이나 짧은 형식(ZipCrypto 1바이트)에서
 * 틀린 비밀번호도 통과시키고, 큰 항목은 확인과 추출에서 두 번 읽게 되므로. 크기를 모르는 항목은 아는 항목 뒤에,
 * 0바이트 항목은 맨 뒤에 둔다(모두 0바이트면 어떤 비밀번호로 풀어도 결과가 빈 파일로 같다).
 * @param encryptedFiles 암호화된 파일 항목들
 * @param sizeOf 항목의 풀린 크기. 모르면 null
 * @return 확인에 쓸 항목. [encryptedFiles]가 비었으면 null
 */
fun <T> passwordCheckEntryOf(encryptedFiles: List<T>, sizeOf: (T) -> Long?): T? =
    encryptedFiles.minWithOrNull(compareBy({ sizeOf(it) == 0L }, { sizeOf(it) ?: Long.MAX_VALUE }))

/** 후보 비밀번호로 내용을 풀어 본 결과. */
enum class PasswordCheck {
    /** 내용 검증까지 통과했다. */
    ACCEPTED,
    /** 형식의 비밀번호 검증값이 맞지 않는다. */
    WRONG,
    /** 풀어 본 내용의 검증(CRC·HMAC·데이터 오류)에 실패했다 — 틀린 비밀번호와 손상을 구분할 수 없다. */
    WRONG_OR_DAMAGED,
}
