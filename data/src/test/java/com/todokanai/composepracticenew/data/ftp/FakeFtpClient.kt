package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.parser.MLSxEntryParser
import org.apache.commons.net.ftp.parser.ParserInitializationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * 네트워크 없이 연결된 것처럼 동작하는 테스트용 FTPClient. 데이터 스트림·완료 확인·크기 조회·삭제 명령의 결과를 테스트가 정하고, 호출을 기록한다.
 * [streamContent]가 null이면 데이터 스트림 요청을 550으로 거부하고, 아니면 그 내용을 담은 스트림을 연다.
 */
internal class FakeFtpClient : FTPClient() {
    /** LIST 조회 시 돌려줄 원격 파일 이름. 모두 1바이트 파일로 돌려준다. */
    var remoteFiles: List<String> = emptyList()
    /** true면 LIST를 550으로 거부한다. */
    var listRefused = false
    /** true면 SYST 응답에 맞는 목록 해석기가 없는 서버처럼 LIST를 보내기 전에 ParserInitializationException을 던진다. */
    var listParserUnavailable = false
    /** FEAT로 알릴 기능 이름. 대문자로 비교한다. */
    var features: Set<String> = emptySet()
    /** MLSD 응답 줄 — 디렉터리 FTP 경로 → MLSx 형식 줄. 없는 경로는 550으로 거부한다. */
    var mlsdListings: Map<String, List<String>> = emptyMap()
    /** 목록 조회에 쓴 명령과 경로(예: "MLSD /r"). */
    val listRequests = mutableListOf<String>()
    /** 데이터 스트림으로 내려줄 내용. null이면 스트림 요청을 [streamRefusal] 응답으로 거부한다. */
    var streamContent: ByteArray? = null
    /** 데이터 스트림 요청을 거부할 때 보낼 응답 (코드, 문자열). */
    var streamRefusal: Pair<Int, String> = 550 to "550 Permission denied"
    /** 데이터 스트림을 열 때(응답 읽기 중) 던질 예외. null이면 던지지 않는다. */
    var streamOpenFailure: IOException? = null
    /** 내려받는 데이터 스트림을 읽을 때 던질 예외. null이면 [streamContent]를 끝까지 읽는다. */
    var streamReadFailure: IOException? = null
    /** completePendingCommand의 동작. 예외를 던지게 해 완료 확인 중 연결 끊김·타임아웃을 재현한다. */
    var onCompletePending: () -> Boolean = { true }
    /** mlistFile의 동작. 예외를 던지게 해 크기 조회 중 연결 끊김·타임아웃을 재현한다. */
    var onMlistFile: (String) -> FTPFile? = { null }
    /** deleteFile의 동작. false면 550 응답, 예외를 던지게 해 응답 읽기 실패를 재현한다. */
    var onDeleteFile: (String) -> Boolean = { true }
    /** completePendingCommand가 호출된 횟수. */
    var completePendingCalls = 0
    /** disconnect가 호출된 횟수. */
    var disconnectCalls = 0
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

    override fun isConnected(): Boolean = disconnectCalls == 0
    override fun getReplyCode(): Int = lastReplyCode
    override fun getReplyString(): String = lastReplyString

    override fun disconnect() {
        disconnectCalls++
    }

    override fun completePendingCommand(): Boolean {
        completePendingCalls++
        return onCompletePending()
    }

    override fun mlistFile(pathname: String?): FTPFile? = onMlistFile(pathname.orEmpty())

    override fun hasFeature(feature: String): Boolean = feature.uppercase(Locale.ROOT) in features

    override fun listFiles(pathname: String?): Array<FTPFile> {
        if (listParserUnavailable) throw ParserInitializationException("Unknown parser type: UNKNOWN Type: L8")
        listRequests += "LIST $pathname"
        if (listRefused) return refusedListing()
        reply(226, "226 Transfer complete")
        return remoteFiles.map { name -> FTPFile().apply { this.name = name; type = FTPFile.FILE_TYPE; size = 1 } }.toTypedArray()
    }

    override fun mlistDir(pathname: String?): Array<FTPFile> {
        listRequests += "MLSD $pathname"
        val lines = mlsdListings[pathname] ?: return refusedListing()
        reply(226, "226 Transfer complete")
        return lines.mapNotNull { MLSxEntryParser.parseEntry(it) }.toTypedArray()
    }

    /** 목록 조회를 550으로 거부한 응답을 남기고, commons-net처럼 빈 배열을 돌려준다. */
    private fun refusedListing(): Array<FTPFile> {
        reply(550, "550 No such directory")
        return emptyArray()
    }

    override fun retrieveFileStream(remote: String): InputStream? {
        retrieveRequests += remote
        streamOpenFailure?.let { throw it }
        val content = streamContent ?: return refused()
        reply(150, "150 Opening data connection")
        val readFailure = streamReadFailure ?: return ByteArrayInputStream(content)
        return object : InputStream() {
            override fun read(): Int = throw readFailure
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw readFailure
        }
    }

    override fun storeFileStream(remote: String?): OutputStream? {
        streamOpenFailure?.let { throw it }
        if (streamContent == null) return refused()
        reply(150, "150 Opening data connection")
        return ByteArrayOutputStream()
    }

    override fun deleteFile(pathname: String): Boolean {
        deleteRequests += pathname
        val deleted = onDeleteFile(pathname)
        if (deleted) reply(250, "250 Deleted") else reply(550, "550 Permission denied")
        return deleted
    }

    override fun removeDirectory(pathname: String?): Boolean {
        removeDirectoryRequests += pathname.orEmpty()
        reply(250, "250 Removed")
        return true
    }

    /** 데이터 스트림 요청을 [streamRefusal] 응답으로 거부한 기록을 남기고 null을 돌려준다. */
    private fun <T> refused(): T? {
        reply(streamRefusal.first, streamRefusal.second)
        return null
    }

    /** 직전 명령의 응답을 기록한다. */
    private fun reply(code: Int, text: String) {
        lastReplyCode = code
        lastReplyString = text
    }
}
