package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.parser.MLSxEntryParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * 테스트가 정하는, 명령 하나에 대한 서버 동작.
 * 명령 이름은 "LIST"·"MLSD"·"RETR"·"STOR"·"DELE"·"RMD"·"MKD"·"RENAME"이다.
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
internal class FakeFtpClient : FTPClient() {
    /** 명령 이름 → 그 명령에 서버가 보일 동작. 없으면 정상 응답한다. */
    var serverBehaviors: Map<String, ServerBehavior> = emptyMap()
    /** LIST 조회 시 돌려줄 원격 파일 이름. 모두 1바이트 파일로 돌려준다. */
    var remoteFiles: List<String> = emptyList()
    /** FEAT로 알릴 기능 이름. 대문자로 비교한다. */
    var features: Set<String> = emptySet()
    /** MLSD 응답 줄 — 디렉터리 FTP 경로 → MLSx 형식 줄. 없는 경로는 550으로 거부한다. */
    var mlsdListings: Map<String, List<String>> = emptyMap()
    /** 목록 조회에 쓴 명령과 경로(예: "MLSD /r"). */
    val listRequests = mutableListOf<String>()
    /** 데이터 스트림으로 내려줄 내용. null이면 스트림 요청을 [streamRefusal] 응답으로 거부한다. */
    var streamContent: ByteArray? = null
    /** 데이터 스트림 요청을 거부할 때 보낼 응답. */
    var streamRefusal: ServerBehavior.Refuse = ServerBehavior.Refuse(550, "550 Permission denied")
    /** 내려받는 데이터 스트림을 읽을 때 던질 예외. null이면 [streamContent]를 끝까지 읽는다. */
    var streamReadFailure: Exception? = null
    /** isConnected가 호출될 때 실행할 동작. 다른 스레드에서 상태 잠금을 쥔 채 멈추게 해 잠금 경합을 재현한다. */
    @Volatile var onIsConnected: () -> Unit = {}
    /** completePendingCommand의 동작. 예외를 던지게 해 완료 확인 중 연결 끊김·타임아웃을 재현한다. */
    var onCompletePending: () -> Boolean = { true }
    /** MLST(크기 조회)의 응답 줄. 첫 줄은 응답 코드로 시작한다. 예외를 던지게 해 응답 읽기 중 연결 끊김·타임아웃·형식 오류를 재현한다. */
    var onMlst: (String) -> List<String> = { listOf("500 MLST not understood") }
    /** deleteFile의 경로별 동작. false면 550 응답, 예외를 던지게 해 응답 읽기 실패를 재현한다. */
    var onDeleteFile: (String) -> Boolean = { true }
    /** completePendingCommand가 호출된 횟수. */
    var completePendingCalls = 0
    /** disconnect가 호출된 횟수. */
    var disconnectCalls = 0
    /** 서버에 보낸 명령 이름(예: "DELE"). 목록 조회·스트림·변경 명령만 기록한다. */
    val sentCommands = mutableListOf<String>()
    /** retrieveFileStream으로 요청된 원격 경로. */
    val retrieveRequests = mutableListOf<String>()
    /** deleteFile로 요청된 원격 경로. */
    val deleteRequests = mutableListOf<String>()
    /** removeDirectory로 요청된 원격 경로. */
    val removeDirectoryRequests = mutableListOf<String>()
    /** 직전 명령의 응답 코드. */
    private var lastReplyCode = 0
    /** 직전 명령의 응답 문자열. */
    private var lastReplyString = ""
    /** 직전 MLST 응답의 모든 줄. */
    private var lastReplyLines: List<String> = emptyList()

    /** 등록된 응답 이벤트 수신기 수. */
    val protocolListenerCount: Int get() = commandSupport.listenerCount

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
        completePendingCalls++
        return onCompletePending()
    }

    override fun sendCommand(command: FTPCmd, args: String?): Int {
        check(command == FTPCmd.MLST) { "가짜 클라이언트가 처리하지 않는 명령: $command" }
        lastReplyLines = onMlst(args.orEmpty())
        val code = lastReplyLines.first().take(3).toInt()
        reply(code, lastReplyLines.joinToString("\n"))
        return code
    }

    override fun getReplyStrings(): Array<String> = lastReplyLines.toTypedArray()

    override fun hasFeature(feature: String): Boolean = feature.uppercase(Locale.ROOT) in features

    override fun listFiles(pathname: String?): Array<FTPFile> {
        listRequests += "LIST $pathname"
        return transferListing("LIST") {
            remoteFiles.map { name -> FTPFile().apply { this.name = name; type = FTPFile.FILE_TYPE; size = 1 } }.toTypedArray()
        }
    }

    override fun mlistDir(pathname: String?): Array<FTPFile> {
        listRequests += "MLSD $pathname"
        val lines = mlsdListings[pathname]
            ?: return respond("MLSD", refused = { emptyArray() }) { reply(550, "550 No such directory"); emptyArray() }
        return transferListing("MLSD") { lines.mapNotNull { MLSxEntryParser.parseEntry(it) }.toTypedArray() }
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

    override fun storeFileStream(remote: String?): OutputStream? =
        respond("STOR", refused = { null }) {
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

    override fun removeDirectory(pathname: String?): Boolean {
        removeDirectoryRequests += pathname.orEmpty()
        return respond("RMD", refused = { false }) { completed(250, "250 Removed") }
    }

    override fun makeDirectory(pathname: String?): Boolean =
        respond("MKD", refused = { false }) { completed(257, "257 Created") }

    override fun rename(from: String?, to: String?): Boolean =
        respond("RENAME", refused = { false }) { completed(250, "250 Renamed") }

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
     * 목록 조회 [command]에 정해 둔 서버 동작을 따른다. commons-net처럼 거부되면 빈 배열을 돌려준다.
     * 정상이면 150으로 전송을 시작해 [entries]를 보내고 226으로 끝내며, 전송 뒤 거부면 150으로 [entries]를 보낸 뒤 거부 응답으로 끝낸다.
     * @param command 목록 조회 명령 이름
     * @param entries 데이터 연결로 보낼 항목
     * @return 클라이언트가 받은 항목
     */
    private inline fun transferListing(command: String, entries: () -> Array<FTPFile>): Array<FTPFile> {
        val behavior = serverBehaviors[command]
        if (behavior is ServerBehavior.RefuseAfterData) {
            sentCommands += command
            reply(150, "150 Here comes the directory listing")
            return entries().also { reply(behavior.code, behavior.text) }
        }
        return respond(command, refused = { emptyArray() }) {
            reply(150, "150 Here comes the directory listing")
            entries().also { reply(226, "226 Transfer complete") }
        }
    }

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
}
