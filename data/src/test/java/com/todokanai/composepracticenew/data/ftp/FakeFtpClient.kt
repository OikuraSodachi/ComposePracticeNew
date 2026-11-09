package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * 테스트가 정하는, 명령 하나에 대한 서버 동작.
 * 명령 이름은 "CWD"·"LIST"·"MLSD"·"MLST"·"RETR"·"STOR"·"DELE"·"RMD"·"MKD"·"RENAME"이다.
 */
internal sealed interface ServerBehavior {
    /**
     * 서버가 명령을 거부 응답으로 받는다. 응답을 끝까지 읽은 것이므로 제어 채널은 살아 있다.
     * @param code 응답 코드
     * @param text 응답 문자열
     */
    data class Refuse(val code: Int, val text: String) : ServerBehavior

    /**
     * commons-net이 명령 중 예외를 던진다 — 연결 닫힘(FTPConnectionClosedException)·응답 타임아웃·구현 오류 등을 재현한다.
     * @param error 던질 예외
     */
    data class Fail(val error: Exception) : ServerBehavior

    /**
     * 서버가 데이터 연결을 열어(150) 목록을 보낸 뒤 완료 응답 대신 거부 응답으로 끝낸다 — 전송 중단을 재현한다. 목록 조회 명령에만 쓴다.
     * @param code 마지막 응답 코드
     * @param text 마지막 응답 문자열
     */
    data class RefuseAfterData(val code: Int, val text: String) : ServerBehavior
}

/**
 * 네트워크 없이 연결된 것처럼 동작하는 테스트용 FTPClient. 명령마다 서버 동작([serverBehaviors])과 완료 확인·크기 조회·삭제의 결과를 테스트가 정하고, 호출을 기록한다.
 * [serverBehaviors]에 없는 명령은 정상 응답한다. [streamContent]가 null이면 데이터 스트림 요청을 [streamRefusal]로 거부한다.
 */
internal class FakeFtpClient : SessionFtpClient() {
    /** 명령 이름 → 그 명령에 서버가 보일 동작. 없으면 정상 응답한다. */
    var serverBehaviors: Map<String, ServerBehavior> = emptyMap()
    /** LIST 조회 시 돌려줄 원격 파일 이름. 모두 1바이트 파일로 돌려준다. */
    var remoteFiles: List<String> = emptyList()
    /** LIST 조회 시 돌려줄 원격 디렉터리 이름. 어느 디렉터리를 조회해도 같은 이름을 돌려준다. */
    var remoteDirectories: List<String> = emptyList()
    /** FEAT로 알릴 기능 이름. 대문자로 비교한다. */
    var features: Set<String> = emptySet()
    /** MLSD 응답 줄 — 디렉터리 FTP 경로 → MLSx 형식 줄. 없는 경로는 550으로 거부한다. */
    var mlsdListings: Map<String, List<String>> = emptyMap()
    /** 디렉터리 FTP 경로 → MLSD 응답 줄. null이면 550으로 거부한다. 기본은 [mlsdListings]를 찾고, 경로마다 다른 목록을 끝없이 만들려면 바꾼다. */
    var mlsdListingOf: (String) -> List<String>? = { mlsdListings[it] }
    /** 목록 조회에 쓴 명령과 경로(예: "MLSD /r", LIST 전 디렉터리 이동은 "CWD /r", 경로 없는 LIST는 "LIST"). */
    val listRequests = mutableListOf<String>()
    /** 데이터 스트림으로 내려줄 내용. null이면 스트림 요청을 [streamRefusal] 응답으로 거부한다. */
    var streamContent: ByteArray? = null
    /** 데이터 스트림 요청을 거부할 때 보낼 응답. */
    var streamRefusal: ServerBehavior.Refuse = ServerBehavior.Refuse(550, "550 Permission denied")
    /** 내려받는 데이터 스트림을 읽을 때 던질 예외. null이면 [streamContent]를 끝까지 읽는다. */
    var streamReadFailure: Exception? = null
    /** 목록 데이터 스트림을 읽을 때 던질 예외. null이면 목록을 끝까지 읽는다. */
    var listingReadFailure: Exception? = null
    /** 목록 데이터의 줄 끝. 기본은 CRLF이며, 줄을 CR로만 끝내는 서버를 재현하려면 바꾼다. */
    var listingLineEnd: String = "\r\n"
    /** 마지막으로 연 목록 데이터 스트림. 읽기가 멈춘 뒤 남은 양([InputStream.available])으로 목록을 끝까지 받았는지 확인한다. */
    var lastListingStream: InputStream? = null
        private set
    /** 목록 조회의 완료 응답을 읽을 때 던질 예외. 완료 응답을 읽다 연결 끊김·타임아웃이 난 것을 재현한다. null이면 완료 응답을 읽는다. */
    var listingCompletionFailure: Exception? = null
    /** isConnected가 호출될 때 실행할 동작. 다른 스레드에서 상태 잠금을 쥔 채 멈추게 해 잠금 경합을 재현한다. */
    @Volatile var onIsConnected: () -> Unit = {}
    /** completePendingCommand의 동작. 예외를 던지게 해 완료 확인 중 연결 끊김·타임아웃을 재현한다. */
    var onCompletePending: () -> Boolean = { true }
    /** MLST(크기 조회)의 응답 줄. 첫 줄은 응답 코드로 시작한다. 예외를 던지게 해 응답 읽기 중 연결 끊김·타임아웃·형식 오류를 재현한다. */
    var onMlst: (String) -> List<String> = { listOf("500 MLST not understood") }
    /** changeWorkingDirectory의 경로별 동작. false면 550 응답. 모든 이동을 거부하려면 [serverBehaviors]의 "CWD"를 쓴다. */
    var onChangeWorkingDirectory: (String) -> Boolean = { true }
    /** deleteFile의 경로별 동작. false면 550 응답, 예외를 던지게 해 응답 읽기 실패를 재현한다. */
    var onDeleteFile: (String) -> Boolean = { true }
    /** 목록 조회가 아닌 명령(전송)의 완료 응답을 읽으려고 completePendingCommand가 호출된 횟수. */
    var completePendingCalls = 0
    /** 목록 조회의 완료 응답을 읽으려고 completePendingCommand가 호출된 횟수. */
    var listingCompletionCalls = 0
    /** disconnect가 호출된 횟수. */
    var disconnectCalls = 0
    /** 서버에 보낸 명령 이름(예: "DELE"). 목록 조회·크기 조회(MLST)·스트림·변경 명령만 기록한다. */
    val sentCommands = mutableListOf<String>()
    /** retrieveFileStream으로 요청된 원격 경로. */
    val retrieveRequests = mutableListOf<String>()
    /** deleteFile로 요청된 원격 경로. */
    val deleteRequests = mutableListOf<String>()
    /** removeDirectory로 요청된 원격 경로. */
    val removeDirectoryRequests = mutableListOf<String>()
    /** storeFileStream으로 요청된 원격 경로. */
    val storeRequests = mutableListOf<String>()
    /** makeDirectory로 요청된 원격 경로. */
    val makeDirectoryRequests = mutableListOf<String>()
    /** rename으로 요청된 (원래 경로, 새 경로). */
    val renameRequests = mutableListOf<Pair<String, String>>()
    /** 직전 명령의 응답 코드. */
    private var lastReplyCode = 0
    /** 직전 명령의 응답 문자열. */
    private var lastReplyString = ""
    /** 직전 MLST 응답의 모든 줄. */
    private var lastReplyLines: List<String> = emptyList()
    /** 목록 데이터 스트림을 연 뒤 [completePendingCommand]가 읽을 완료 응답(코드와 문자열). 목록 조회 중이 아니면 null이다. */
    private var pendingListingReply: Pair<Int, String>? = null

    override fun isConnected(): Boolean {
        onIsConnected()
        return disconnectCalls == 0
    }
    override fun getReplyCode(): Int = lastReplyCode
    override fun getReplyString(): String = lastReplyString

    override fun disconnect() {
        disconnectCalls++
    }

    override fun completePendingCommand(): Boolean {
        val listingReply = pendingListingReply ?: run {
            completePendingCalls++
            return onCompletePending()
        }
        listingCompletionCalls++
        pendingListingReply = null
        listingCompletionFailure?.let { throw it }
        reply(listingReply.first, listingReply.second)
        return FTPReply.isPositiveCompletion(listingReply.first)
    }

    override fun sendCommand(command: FTPCmd, args: String?): Int {
        check(command == FTPCmd.MLST) { "가짜 클라이언트가 처리하지 않는 명령: $command" }
        return respond("MLST", refused = {
            lastReplyLines = listOf(lastReplyString)
            lastReplyCode
        }) {
            lastReplyLines = onMlst(args.orEmpty())
            val code = lastReplyLines.first().take(3).toInt()
            reply(code, lastReplyLines.joinToString("\n"))
            code
        }
    }

    override fun getReplyStrings(): Array<String> = lastReplyLines.toTypedArray()

    override fun hasFeature(feature: String): Boolean = feature.uppercase(Locale.ROOT) in features

    override fun changeWorkingDirectory(pathname: String?): Boolean {
        listRequests += "CWD $pathname"
        return respond("CWD", refused = { false }) {
            if (onChangeWorkingDirectory(pathname.orEmpty())) completed(250, "250 Directory changed")
            else false.also { reply(550, "550 Permission denied") }
        }
    }

    override fun getSystemType(): String = SYSTEM_TYPE

    override fun openListStream(): InputStream? {
        listRequests += "LIST"
        return listingStream("LIST") {
            remoteDirectories.map { name -> unixListLine(DIRECTORY_MODE, size = 0, name) } + remoteFiles.map { name -> unixListLine(FILE_MODE, size = 1, name) }
        }
    }

    override fun openMlsdStream(pathname: String): InputStream? {
        listRequests += "MLSD $pathname"
        val lines = mlsdListingOf(pathname)
            ?: return respond("MLSD", refused = { null }) { reply(550, "550 No such directory"); null }
        return listingStream("MLSD") { lines }
    }

    override fun retrieveFileStream(remote: String): InputStream? {
        retrieveRequests += remote
        return respond("RETR", refused = { null }) {
            val content = streamContent ?: return@respond refuseStream()
            reply(150, "150 Opening data connection")
            val readFailure = streamReadFailure ?: return@respond ByteArrayInputStream(content)
            object : InputStream() {
                override fun read(): Int = throw readFailure
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw readFailure
            }
        }
    }

    override fun storeFileStream(remote: String): OutputStream? =
        respond("STOR", refused = { null }) {
            storeRequests += remote
            if (streamContent == null) return@respond refuseStream()
            reply(150, "150 Opening data connection")
            ByteArrayOutputStream()
        }

    override fun deleteFile(pathname: String): Boolean {
        deleteRequests += pathname
        return respond("DELE", refused = { false }) {
            val deleted = onDeleteFile(pathname)
            if (deleted) reply(250, "250 Deleted") else reply(550, "550 Permission denied")
            deleted
        }
    }

    override fun removeDirectory(pathname: String): Boolean {
        removeDirectoryRequests += pathname
        return respond("RMD", refused = { false }) { completed(250, "250 Removed") }
    }

    override fun makeDirectory(pathname: String): Boolean {
        makeDirectoryRequests += pathname
        return respond("MKD", refused = { false }) { completed(257, "257 Created") }
    }

    override fun rename(from: String, to: String): Boolean {
        renameRequests += from to to
        return respond("RENAME", refused = { false }) { completed(250, "250 Renamed") }
    }

    /**
     * [command]에 정해 둔 서버 동작을 따른다. 거부면 응답을 남기고 [refused]를, 예외면 그 예외를 던지고, 없으면 [normal]을 실행한다.
     * 전송 뒤 거부([ServerBehavior.RefuseAfterData])는 목록 조회만 쓰므로 [transferListing]이 처리한다.
     * @param command 명령 이름
     * @param refused 거부됐을 때 commons-net이 돌려주는 값
     * @param normal 정상 응답일 때의 동작
     * @return 명령의 반환값
     */
    private inline fun <T> respond(command: String, refused: () -> T, normal: () -> T): T {
        sentCommands += command
        return when (val behavior = serverBehaviors[command]) {
            null -> normal()
            is ServerBehavior.Refuse -> {
                reply(behavior.code, behavior.text)
                refused()
            }
            is ServerBehavior.Fail -> throw behavior.error
            is ServerBehavior.RefuseAfterData -> error("전송 뒤 거부는 목록 조회 명령에만 쓴다: $command")
        }
    }

    /**
     * 목록 조회 [command]에 정해 둔 서버 동작을 따른다. commons-net처럼 데이터 연결을 열기 전에 거부되면 null을 돌려준다.
     * 정상이면 150으로 전송을 시작해 [lines]를 보내고 완료 응답을 226으로, 전송 뒤 거부면 150으로 [lines]를 보낸 뒤 완료 응답을 거부 응답으로 남긴다.
     * 완료 응답은 [completePendingCommand]가 읽는다.
     * @param command 목록 조회 명령 이름
     * @param lines 데이터 연결로 보낼 목록 줄
     * @return 목록 데이터 스트림, 거부되면 null
     */
    private fun listingStream(command: String, lines: () -> List<String>): InputStream? {
        val behavior = serverBehaviors[command]
        if (behavior is ServerBehavior.RefuseAfterData) {
            sentCommands += command
            return startListing(lines(), finalReply = behavior.code to behavior.text)
        }
        return respond(command, refused = { null }) { startListing(lines(), finalReply = 226 to "226 Transfer complete") }
    }

    /**
     * 150으로 목록 전송을 시작하고, [finalReply]를 [completePendingCommand]가 읽을 완료 응답으로 남긴다.
     * @param lines 데이터 연결로 보낼 목록 줄
     * @param finalReply 완료 응답의 코드와 문자열
     * @return 목록 데이터 스트림. [listingReadFailure]가 있으면 읽을 때 그 예외를 던진다
     */
    private fun startListing(lines: List<String>, finalReply: Pair<Int, String>): InputStream {
        reply(150, "150 Here comes the directory listing")
        pendingListingReply = finalReply
        val readFailure = listingReadFailure
        val stream = if (readFailure == null) {
            ByteArrayInputStream(lines.joinToString("") { "$it$listingLineEnd" }.toByteArray(charset(controlEncoding)))
        } else {
            object : InputStream() {
                override fun read(): Int = throw readFailure
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw readFailure
            }
        }
        return stream.also { lastListingStream = it }
    }

    /**
     * 유닉스 계열 LIST 목록 줄 하나를 만든다. commons-net이 [SYSTEM_TYPE]으로 고르는 해석기가 읽는 형식이다.
     * @param mode 종류와 권한(예: "drwxr-xr-x")
     * @param size 크기(바이트)
     * @param name 항목 이름
     * @return LIST 목록 줄
     */
    private fun unixListLine(mode: String, size: Int, name: String): String = "$mode 1 user group $size Jan  1 00:00 $name"

    /** 데이터 스트림 요청을 [streamRefusal] 응답으로 거부한 기록을 남기고 null을 돌려준다. */
    private fun <T> refuseStream(): T? {
        reply(streamRefusal.code, streamRefusal.text)
        return null
    }

    /** 성공 응답을 남기고 true를 돌려준다. */
    private fun completed(code: Int, text: String): Boolean {
        reply(code, text)
        return true
    }

    /** 직전 명령의 응답을 기록하고, 실제 FTPClient처럼 응답 이벤트를 수신기에 알린다. */
    private fun reply(code: Int, text: String) {
        lastReplyCode = code
        lastReplyString = text
        fireReplyReceived(code, text)
    }

    private companion object {
        /** SYST로 알릴 시스템 종류. commons-net은 이 값으로 유닉스 계열 LIST 해석기를 고른다. */
        const val SYSTEM_TYPE = "UNIX Type: L8"
        /** LIST 목록 줄의 디렉터리 종류·권한. */
        const val DIRECTORY_MODE = "drwxr-xr-x"
        /** LIST 목록 줄의 파일 종류·권한. */
        const val FILE_MODE = "-rw-r--r--"
    }
}
