package com.todokanai.composepracticenew.model

/**
 * 진행 중인 작업이 멈추고 사용자에게 답을 기다리는 질문. 질문함은 종류와 무관하게 맡긴 순서대로 하나씩 묻는다 —
 * 여러 작업의 질문이 한꺼번에 떠서 서로 가리지 않도록.
 */
sealed interface OperationQuestion {
    /** 질문 식별자. 답할 때 어느 질문인지 가리킨다. */
    val id: Long

    /** 질문한 작업의 종류. */
    val operationType: FileOperationType
}

/** 작업이 묻는 질문의 종류. 답을 기다리는 알림과 답이 없어 멈춘 작업의 알림 문구를 고르는 데 쓴다. */
enum class QuestionKind {
    /** 쓰려는 자리에 같은 이름의 항목이 있다([ConflictQuestion]). */
    CONFLICT,
    /** 암호화된 압축 파일의 비밀번호가 필요하다([PasswordQuestion]). */
    ARCHIVE_PASSWORD,
}

/** 압축 파일의 비밀번호를 왜 묻는지. 다시 묻는 경우 앞서 입력한 비밀번호가 어떻게 됐는지 알린다. */
enum class ArchivePasswordPrompt {
    /** 이 압축 파일(또는 앞서 확인한 비밀번호가 맞지 않는 항목)의 비밀번호를 처음 묻는다. */
    FIRST,
    /** 앞서 입력한 비밀번호가 틀렸다. 형식의 비밀번호 검증값으로 틀린 것을 확인했다. */
    WRONG,
    /**
     * 앞서 입력한 비밀번호로 풀었지만 내용 검증(CRC 등)에 실패했다. 틀린 비밀번호와 손상된 파일을 구분할 수 없는 형식(RAR4, ZipCrypto)이거나,
     * 검증값을 우연히 통과한 틀린 비밀번호다.
     */
    WRONG_OR_DAMAGED,
}

/**
 * 압축 해제 중 암호화된 압축 파일의 비밀번호를 기다리는 질문.
 * @param id 질문 식별자
 * @param operationType 질문한 작업의 종류
 * @param archiveName 비밀번호를 묻는 압축 파일의 이름
 * @param prompt 묻는 이유(처음·틀림·틀렸거나 손상)
 */
data class PasswordQuestion(
    override val id: Long,
    override val operationType: FileOperationType,
    val archiveName: String,
    val prompt: ArchivePasswordPrompt,
) : OperationQuestion

/** 사용자가 비밀번호 질문에 준 답. */
sealed interface PasswordAnswer {
    /**
     * 입력한 비밀번호로 푼다.
     * @param password 입력한 비밀번호(비어 있지 않다)
     */
    data class Enter(val password: String) : PasswordAnswer {
        init {
            require(password.isNotEmpty()) { "비밀번호는 비어 있을 수 없습니다" }
        }

        // 비밀번호가 로그·예외 메시지에 찍히지 않도록 내용을 숨긴다
        override fun toString(): String = "Enter(password=***)"
    }

    /** 작업을 취소한다. 이미 푼 항목은 그대로 둔다. */
    data object Cancel : PasswordAnswer
}
