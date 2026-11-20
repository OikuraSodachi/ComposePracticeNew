package com.todokanai.composepracticenew.model

/**
 * 화면에 알릴 파일 작업 메시지의 내용. 문장 대신 무엇을 알릴지만 담고, 표시 문구는 app 레이어가 종류별로 문자열 리소스에서 정한다(#218).
 * 서버 응답 원문·항목 이름처럼 번역하지 않는 값은 각 종류의 필드로 담는다.
 */
sealed interface NoticeMessage {

    /**
     * 작업을 마쳤다.
     * @param kind 마친 작업
     */
    data class Completed(val kind: OperationKind) : NoticeMessage

    /**
     * 사용자가 작업을 취소했다. 이미 처리한 항목이 어떻게 남는지는 작업 종류로 정한다.
     * @param kind 취소한 작업
     */
    data class Cancelled(val kind: OperationKind) : NoticeMessage

    /**
     * 작업이 실패 결과를 돌려줬다.
     * @param kind 실패한 작업. 실패 문구의 머리말을 정한다
     * @param failure 작업이 돌려준 실패. 원인에는 작업 맥락이 붙어 있지 않다
     */
    data class OperationFailed(val kind: OperationKind, val failure: IoResult.Failure) : NoticeMessage

    /**
     * 작업의 질문 하나에 정해진 시간 동안 답이 없어 작업을 멈췄다. 이미 처리한 항목은 그대로 둔다.
     * @param operationType 멈춘 작업의 종류
     * @param kind 답을 받지 못한 질문의 종류
     * @param name 질문이 가리킨 이름(충돌이면 겹친 항목, 비밀번호면 압축 파일)
     * @param waitHours 답을 기다린 시간(시간 단위)
     */
    data class UnansweredQuestion(val operationType: FileOperationType, val kind: QuestionKind, val name: String, val waitHours: Long) : NoticeMessage

    /**
     * 사용자가 정한 이름이 규칙에 맞지 않아 작업을 실행하지 않았다.
     * @param operation 이름을 쓰려던 작업
     * @param name 거부한 이름(압축이면 확장자 포함)
     */
    data class InvalidName(val operation: NamingOperation, val name: String) : NoticeMessage

    /**
     * 목적지가 원본 폴더 자신이거나 그 안이라 복사·이동을 실행하지 않았다.
     * @param operation 실행하지 않은 작업
     * @param sourceName 목적지를 품은 원본 폴더의 표시 이름
     */
    data class DestinationInsideSource(val operation: RelocateOperation, val sourceName: String) : NoticeMessage

    /**
     * 목적지가 원본이 있는 폴더라 복사·이동을 실행하지 않았다.
     * @param operation 실행하지 않은 작업
     */
    data class SameDestination(val operation: RelocateOperation) : NoticeMessage

    /**
     * 저장소가 만들 수 없는 압축 형식이라 압축을 실행하지 않았다.
     * @param extension 요청한 형식의 확장자
     */
    data class UnsupportedArchiveFormat(val extension: String) : NoticeMessage

    /**
     * 파일을 다른 앱으로 열지 못했다.
     * @param reason 열지 못한 이유
     * @param name 열려던 파일의 이름
     */
    data class OpenFailed(val reason: OpenFailure, val name: String) : NoticeMessage

    /** 목적지 폴더의 목록을 확인하지 못해 이름 충돌을 판정할 수 없어 작업(업로드·이름 변경)을 실행하지 않았다. */
    data object DestinationUnverified : NoticeMessage

    /** 연결된 원격 세션이 없어 업로드를 시작하지 않았다. 원격 연결은 저장소 화면에서 서버를 골라야 시작된다. */
    data object NoRemoteSession : NoticeMessage

    /**
     * 읽을 수 없는 폴더라 이동하지 않았다.
     * @param name 열지 못한 폴더의 표시 이름
     */
    data class FolderUnreadable(val name: String) : NoticeMessage

    /**
     * 취소한 업로드의 받다 만 파일을 서버에서 지우지 못해 남았을 수 있다.
     * @param path 받다 만 파일의 원격 경로
     * @param cause 지우지 못한 이유
     */
    data class UploadLeftover(val path: String, val cause: LeftoverCause) : NoticeMessage
}

/**
 * 이 메시지의 종류. 메시지 내용마다 종류는 하나로 정해지므로 맡기는 쪽이 고르지 않고 여기서만 정한다(#461) —
 * 맡기는 곳마다 고르면 짝이 어긋나도 컴파일러가 잡지 못한다. 새 메시지를 더하면 이 when이 종류를 정하라고 알려 준다.
 */
val NoticeMessage.kind: OperationNoticeKind
    get() = when (this) {
        is NoticeMessage.Completed, is NoticeMessage.Cancelled -> OperationNoticeKind.COMPLETION
        is NoticeMessage.OperationFailed,
        is NoticeMessage.UnansweredQuestion,
        is NoticeMessage.InvalidName,
        is NoticeMessage.DestinationInsideSource,
        is NoticeMessage.SameDestination,
        is NoticeMessage.UnsupportedArchiveFormat,
        is NoticeMessage.OpenFailed,
        NoticeMessage.DestinationUnverified,
        NoticeMessage.NoRemoteSession,
        is NoticeMessage.FolderUnreadable,
        is NoticeMessage.UploadLeftover -> OperationNoticeKind.FAILURE
    }

/** 사용자가 정한 이름을 쓰는 작업. 이름 거부 메시지의 머리말을 정한다. */
enum class NamingOperation {
    /** 이름 변경. */
    RENAME,

    /** 새 폴더 만들기. */
    MAKE_FOLDER,

    /** 압축 파일 만들기. */
    COMPRESS,
}

/** 원본을 다른 폴더에 두는 작업. 목적지 거부 메시지의 머리말을 정한다. */
enum class RelocateOperation {
    /** 복사. */
    COPY,

    /** 이동. */
    MOVE,
}

/** 파일을 다른 앱으로 열지 못한 이유. */
enum class OpenFailure {
    /** 파일 형식을 알 수 없다. */
    UNKNOWN_TYPE,

    /** 이 형식을 여는 앱이 없다. */
    NO_HANDLER,

    /** 이 형식을 여는 앱이 이 앱이 여는 것을 허용하지 않았다. */
    REFUSED,
}

/** 취소한 업로드의 받다 만 파일을 지우지 못한 이유. */
sealed interface LeftoverCause {

    /**
     * 서버가 삭제(DELE)를 거부했다.
     * @param reply 서버가 보낸 거부 응답 원문
     */
    data class Refused(val reply: String) : LeftoverCause

    /** 연결이 끊겨 지우지 못했다. */
    data object SessionEnded : LeftoverCause
}
