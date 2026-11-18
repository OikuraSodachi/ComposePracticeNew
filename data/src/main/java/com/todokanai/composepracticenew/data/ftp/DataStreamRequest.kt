package com.todokanai.composepracticenew.data.ftp

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * 데이터 연결을 쓰는 명령 하나의 요청. 보내는 데이터 명령([command])과 그 명령을 보내 데이터 스트림을 여는 동작([open])을 한 타입에 묶는다.
 * 둘을 따로 받으면 어긋나도 컴파일러가 잡지 못하고, 그 명령의 거부가 데이터 연결 준비의 거부로 잘못 보고된다([FtpReplyRules.refusedDataStepOf], #458).
 * @param command 이 요청이 보내는 데이터 명령
 */
internal sealed class DataStreamRequest<S : Closeable>(val command: FtpDataCommand) {

    /**
     * [command]를 보내 데이터 스트림을 연다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @return 열린 데이터 스트림. 서버가 거부하면 null
     */
    abstract fun open(client: FtpCommandClient): S?

    /** 현재 디렉터리의 목록을 인자 없는 LIST로 받는 요청. */
    data object List : DataStreamRequest<InputStream>(FtpDataCommand.LIST) {
        override fun open(client: FtpCommandClient): InputStream? = client.openListStream()
    }

    /**
     * [pathname] 디렉터리의 목록을 MLSD로 받는 요청.
     * @param pathname 목록을 받을 디렉터리의 FTP 경로
     */
    data class Mlsd(val pathname: String) : DataStreamRequest<InputStream>(FtpDataCommand.MLSD) {
        override fun open(client: FtpCommandClient): InputStream? = client.openMlsdStream(pathname)
    }

    /**
     * [remote] 파일을 RETR로 받는 요청.
     * @param remote 받을 파일의 FTP 경로
     */
    data class Retrieve(val remote: String) : DataStreamRequest<InputStream>(FtpDataCommand.RETR) {
        override fun open(client: FtpCommandClient): InputStream? = client.retrieveFileStream(remote)
    }

    /**
     * [remote] 경로에 STOR로 올리는 요청.
     * @param remote 올릴 파일의 FTP 경로
     */
    data class Store(val remote: String) : DataStreamRequest<OutputStream>(FtpDataCommand.STOR) {
        override fun open(client: FtpCommandClient): OutputStream? = client.storeFileStream(remote)
    }
}
