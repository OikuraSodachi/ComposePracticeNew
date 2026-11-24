package com.todokanai.composepracticenew.model

/**
 * 파일 입출력 실패의 원인. 문장 대신 무엇이 실패했는지만 담고, 표시 문구는 app 레이어가 종류별로 문자열 리소스에서 정한다(#218).
 * 서버 응답 원문·예외 메시지·항목 이름처럼 번역하지 않는 값은 각 종류의 필드로 담는다.
 */
sealed interface FailureReason {

    /** 시스템이 프로세스 보호를 거부해 작업을 시작하지 않았다. */
    data object ProtectionRefused : FailureReason

    /**
     * 입출력이 실패했다. 원인은 시스템이 알린 메시지 그대로다.
     * @param detail 시스템이 알린 메시지
     */
    data class IoFailed(val detail: String) : FailureReason

    /**
     * 파일 시스템이 항목에 대한 요청을 거부했다.
     * @param target 거부된 항목(시스템이 알린 경로). 모르면 null
     * @param kind 거부 종류
     */
    data class FileSystemRefused(val target: String?, val kind: FileSystemRefusal) : FailureReason

    /**
     * 폴더 안을 나열하지 못했다.
     * @param kind 나열하지 못한 종류
     */
    data class FolderNotListed(val kind: ListingRefusal) : FailureReason

    /**
     * 폴더가 아니거나 읽을 권한이 없어 폴더 안을 읽지 못했다.
     * @param location 읽지 못한 폴더의 자리. 실패 항목의 경로가 곧 자리이거나 읽으려던 폴더 자신이면 null
     */
    data class FolderUnreadable(val location: String? = null) : FailureReason

    /**
     * 쓸 자리의 여유 공간이 모자란다.
     * @param neededBytes 필요한 바이트 수
     * @param freeBytes 여유 바이트 수
     */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : FailureReason

    /** 쓸 자리에 여유 공간이 전혀 없다. */
    data object NoFreeSpace : FailureReason

    /**
     * 심볼릭 링크라 다루지 않았다(지원 범위 밖).
     * @param location 링크가 있는 자리. 실패 항목의 경로가 곧 자리면 null
     */
    data class UnsupportedLink(val location: String? = null) : FailureReason

    /** 압축 파일 항목의 경로가 압축을 풀 폴더 밖을 가리켜 풀지 않았다. */
    data object UnsafeArchiveEntryPath : FailureReason

    /**
     * 지원하지 않는 압축 형식이다.
     * @param name 형식을 알아본 이름(확장자나 파일 이름)
     */
    data class UnsupportedArchiveFormat(val name: String) : FailureReason

    /**
     * 분할 압축 파일의 볼륨 일부가 없어 풀지 못했다.
     * @param archiveName 압축 해제를 시작한 볼륨의 파일 이름
     * @param volumeName 없는 볼륨의 파일 이름
     */
    data class MissingArchiveVolume(val archiveName: String, val volumeName: String) : FailureReason

    /**
     * 압축 파일이 아니거나 손상되어 열지 못했다.
     * @param name 압축 파일 이름
     */
    data class UnreadableArchive(val name: String) : FailureReason

    /**
     * 압축 파일을 읽는 동안 파일이 바뀌어(줄어들어) 끝까지 읽지 못했다.
     * @param name 바뀐 파일 이름
     */
    data class ArchiveChangedWhileReading(val name: String) : FailureReason

    /**
     * 내용 보기에서 항목을 고른 뒤 압축 파일의 항목 목록이 바뀌어, 고른 항목을 찾을 수 없어 아무것도 풀지 않았다.
     * @param name 압축 파일 이름
     */
    data class ArchiveChangedSinceViewed(val name: String) : FailureReason

    /**
     * 항목을 추가할 수 없는 압축 파일이다(ZIP이 아니거나 분할 압축). 압축 파일을 바꾸지 않았다.
     * @param name 압축 파일 이름
     */
    data class ArchiveNotAppendable(val name: String) : FailureReason

    /**
     * 추가할 항목에 그 압축 파일 자신이 들어 있다(고른 항목 자신이거나 고른 폴더 안). 압축 파일을 바꾸지 않았다.
     * @param name 압축 파일 이름
     */
    data class ArchiveAddedIntoItself(val name: String) : FailureReason

    /** 이름을 읽을 수 없는 항목(해석하지 못한 이름, 이름 없는 항목)이 있어 아무것도 풀지 않았다. */
    data object ArchiveEntryNameUnreadable : FailureReason

    /**
     * 압축 파일에서 확인한 비밀번호가 이 항목에 맞지 않아 풀지 못했다(한 압축 파일 안의 항목마다 다른 비밀번호는 지원 범위 밖).
     * @param entryName 항목 이름
     */
    data class ArchiveEntryPasswordMismatch(val entryName: String) : FailureReason

    /** 비밀번호를 확인할 내용이 있는 항목이 없는 암호화 압축 파일이라, 확인하지 않은 비밀번호로 풀지 않았다. */
    data object ArchivePasswordUnverifiable : FailureReason

    /**
     * 압축 해제 라이브러리가 항목을 풀지 못했다고 알렸다(손상, 지원하지 않는 압축 방식 등).
     * @param entryName 항목 이름. 모르면 null
     * @param result 라이브러리가 알린 결과 이름(번역하지 않는 값)
     */
    data class UnreadableArchiveEntry(val entryName: String?, val result: String) : FailureReason

    /** 압축에 넣을 항목이 없다. */
    data object NothingToCompress : FailureReason

    /**
     * 같은 이름의 항목이 이미 있다.
     * @param name 겹친 이름
     */
    data class NameTaken(val name: String) : FailureReason

    /**
     * 종류(파일·폴더)가 다른 항목이 같은 이름으로 있다.
     * @param location 겹친 자리
     */
    data class KindConflict(val location: String) : FailureReason

    /**
     * 안쪽 항목을 합친 뒤 원래 폴더를 지우지 못했다.
     * @param name 원래 폴더 이름
     */
    data class MergedSourceNotRemoved(val name: String) : FailureReason

    /**
     * 실패한 쓰기가 남긴 파일을 지우지 못해, 그 항목의 자리에 쓰다 만 파일이 남았다(#431).
     * @param cause 쓰기가 실패한 원인. 모르면 null
     * @param removal 남은 파일을 지우지 못한 원인. 모르면 null
     */
    data class LeftoverNotRemoved(val cause: FailureReason?, val removal: FailureReason?) : FailureReason

    /**
     * 원격 서버가 요청을 거부했다.
     * @param step 거부된 단계
     * @param reply 서버가 보낸 응답 원문
     * @param mayHaveHiddenEntries 목록에 보이지 않는 항목이 남아 거부됐을 수 있는지
     */
    data class ServerRefused(val step: RemoteStep, val reply: String, val mayHaveHiddenEntries: Boolean = false) : FailureReason

    /**
     * 원격 단계가 도중에 실패했다.
     * @param step 실패한 단계
     * @param cause 실패 원인
     */
    data class RemoteStepFailed(val step: RemoteStep, val cause: FailureReason) : FailureReason

    /**
     * 데이터를 다 주고받았지만 서버가 전송 완료를 확인하지 않았다.
     * @param reply 서버가 보낸 응답 원문
     */
    data class TransferUnconfirmed(val reply: String) : FailureReason

    /**
     * 전송이 실패했다.
     * @param detail 시스템이 알린 메시지. 없으면 null
     */
    data class TransferFailed(val detail: String?) : FailureReason

    /**
     * 데이터 연결을 열지 못했다.
     * @param detail 시스템이 알린 메시지(없으면 예외 종류)
     */
    data class DataConnectionFailed(val detail: String) : FailureReason

    /** 원격 서버에 연결되어 있지 않다. */
    data object NotConnected : FailureReason

    /** 원격 서버와의 연결이 끊겼다. */
    data object ConnectionClosed : FailureReason

    /**
     * 원격 서버 응답을 받지 못해 연결을 종료했다.
     * @param detail 시스템이 알린 메시지(없으면 예외 종류)
     */
    data class NoResponse(val detail: String) : FailureReason

    /** 원격 서버의 목록 형식을 알 수 없어(시스템 종류를 알려주지 않거나 해석할 수 없는 형식) 그 서버의 목록을 읽을 수 없다. */
    data object ListingFormatUnknown : FailureReason

    /**
     * 원격 목록의 한 줄을 해석하지 못했다.
     * @param line 해석하지 못한 줄(표시용)
     */
    data class UnreadableListingEntry(val line: String) : FailureReason

    /**
     * 폴더 안의 항목이 너무 많아 처리하지 않았다.
     * @param path 그 폴더의 경로
     * @param cycleUndecidable 서버의 목록으로 폴더 순환을 판별할 수 없는지
     */
    data class TooManyEntries(val path: String, val cycleUndecidable: Boolean) : FailureReason

    /**
     * 폴더가 자기 상위 폴더와 같은 폴더다(순환).
     * @param path 그 폴더의 경로
     */
    data class FolderCycle(val path: String) : FailureReason

    /**
     * 경로가 한도보다 길어 처리하지 않았다.
     * @param path 그 경로
     * @param limitBytes 경로 길이 한도(바이트)
     * @param cycleUndecidable 서버의 목록으로 폴더 순환을 판별할 수 없는지
     */
    data class PathTooLong(val path: String, val limitBytes: Int, val cycleUndecidable: Boolean) : FailureReason

    /** 예상하지 못한 오류(구현 오류)로 작업이 끝났다. 내부 예외 메시지는 사용자에게 의미가 없으므로 담지 않는다. */
    data object Unexpected : FailureReason
}

/** 파일 시스템이 항목에 대한 요청을 거부한 종류. */
sealed interface FileSystemRefusal {

    /** 같은 이름의 항목이 이미 있다. */
    data object AlreadyExists : FileSystemRefusal

    /** 권한이 없다. */
    data object AccessDenied : FileSystemRefusal

    /** 항목을 찾을 수 없다. */
    data object NotFound : FileSystemRefusal

    /** 비어 있지 않은 폴더다. */
    data object NotEmpty : FileSystemRefusal

    /** 폴더가 아니다. */
    data object NotFolder : FileSystemRefusal

    /**
     * 그 밖의 파일 시스템 오류.
     * @param exceptionName 시스템이 알린 오류 종류 이름
     */
    data class Other(val exceptionName: String) : FileSystemRefusal
}

/** 폴더 안을 나열하지 못한 종류. */
enum class ListingRefusal {
    /** 폴더가 아니다. */
    NOT_FOLDER,

    /** 읽을 권한이 없다. */
    ACCESS_DENIED,

    /** 항목이 없다. */
    NOT_FOUND,
}

/** 원격 작업의 단계. 프로토콜의 명령이 아니라 무엇을 하던 중이었는지로 나눈다. */
enum class RemoteStep {
    /** 폴더로 들어가기. */
    OPEN_FOLDER,

    /** 폴더 목록 읽기. */
    LIST_FOLDER,

    /** 이름 바꾸기. */
    RENAME,

    /** 파일 지우기. */
    DELETE_FILE,

    /** 폴더 만들기. */
    MAKE_FOLDER,

    /** 폴더 지우기. */
    REMOVE_FOLDER,

    /** 내려받기. */
    DOWNLOAD,

    /** 올리기. */
    UPLOAD,

    /** 데이터 연결 준비. */
    PREPARE_TRANSFER,

    /** 작업용 연결 열기 — 진행 표시가 있는 원격 작업이 자기 연결을 열어 로그인하는 단계(#436). */
    OPEN_SESSION,
}
