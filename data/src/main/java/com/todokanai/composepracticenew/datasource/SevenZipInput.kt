package com.todokanai.composepracticenew.datasource

import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 7-Zip이 아카이브를 열 때 다른 볼륨을 받는 통로. 열기 콜백([SevenZipOpenCallback])이 라이브러리의 볼륨 요청을 여기에 맡긴다.
 */
internal interface SevenZipVolumeSource {

    /** 라이브러리가 다음 볼륨의 이름을 계산하는 데 쓰는, 마지막으로 연 볼륨의 경로. */
    val currentVolumeName: String

    /**
     * 라이브러리가 요청한 볼륨을 연다.
     * @param name 라이브러리가 계산한 볼륨 경로
     * @return 볼륨 스트림. 없으면 null
     */
    fun open(name: String): IInStream?
}

/**
 * 7-Zip에 넘기는 아카이브 입력. 아카이브를 다 쓸 때까지(추출이 끝날 때까지) 연 파일을 열어 두고, 닫을 때 모두 닫는다.
 * 볼륨을 읽는 방식([VolumeLayout])마다 구현이 다르다.
 */
internal sealed class SevenZipInput : SevenZipVolumeSource, Closeable {

    /** 아카이브를 처음부터 읽는 스트림을 만든다. 형식을 바꿔 열어 볼 때마다 부른다. */
    abstract fun stream(): IInStream

    /**
     * 라이브러리가 요청했지만 없었던 첫 볼륨의 파일 이름. 없으면 null.
     * 라이브러리는 볼륨이 몇 개인지 모른 채 다음 이름을 물을 수 있어, 이 값만으로는 빠진 볼륨인지 세트의 끝인지 알 수 없다 —
     * 아카이브를 열거나 풀지 못했을 때, 또는 아카이브가 오류를 알릴 때만 빠진 볼륨으로 본다.
     */
    abstract val missingVolume: String?

    /**
     * 시작 파일을 열고, 라이브러리가 다른 볼륨을 요청하면 같은 폴더에서 찾아 연다(RAR 분할, 단일 아카이브).
     * @param first 시작 파일
     */
    class SelfLocating(private val first: File) : SevenZipInput() {

        /** 연 볼륨 파일. 라이브러리가 같은 볼륨을 다시 요청하면 이것을 쓴다. */
        private val opened = LinkedHashMap<String, RandomAccessFile>()

        override var currentVolumeName: String = first.path
            private set

        override var missingVolume: String? = null
            private set

        // 다시 열 때마다 볼륨 요청 기록을 처음으로 돌린다 — 라이브러리는 첫 볼륨을 연 직후 이름을 물어 그 다음 번호부터 요청하므로,
        // 이전 열기의 마지막 볼륨 이름이 남으면 앞 볼륨을 건너뛰고, 이전 열기에서 없던 볼륨이 이번 열기의 빠진 볼륨으로 남는다
        override fun stream(): IInStream {
            currentVolumeName = first.path
            missingVolume = null
            return streamOf(first)
        }

        override fun open(name: String): IInStream? {
            val volume = File(name).let { if (it.isAbsolute) it else File(first.parentFile, name) }
            if (!volume.isFile) {
                if (missingVolume == null) missingVolume = volume.name
                return null
            }
            currentVolumeName = volume.path
            return streamOf(volume)
        }

        override fun close() = closeAll(opened.values)

        /**
         * [file]을 처음부터 읽는 스트림을 만든다. 파일은 한 번만 열어 두고, 줄 때마다 읽기 위치를 처음으로 돌린다 —
         * 7-Zip은 받은 스트림의 지금 위치를 아카이브(볼륨)의 시작으로 보는데, 열어 둔 파일은 이전 열기(비밀번호 없이 열다 실패한 열기 등)가 읽은 위치에 남아 있으므로.
         * @param file 볼륨 파일
         * @return 처음 위치의 스트림
         */
        private fun streamOf(file: File): IInStream {
            val raf = opened.getOrPut(file.path) { RandomAccessFile(file, "r") }
            raf.seek(0)
            return RandomAccessFileInStream(raf)
        }
    }

    /**
     * 번호 볼륨(`.001`, `.002` …)을 이어 붙인 하나의 스트림으로 읽는다(7z 분할). 라이브러리는 다른 볼륨을 요청하지 않는다.
     * @param volumes 번호 순서의 볼륨 파일들
     */
    class Numbered(volumes: List<File>) : SevenZipInput() {

        /** 이어 붙인 볼륨. */
        private val joined = ConcatenatedInStream(volumes)

        override val currentVolumeName: String = volumes.first().path

        override val missingVolume: String? = null

        override fun stream(): IInStream = joined.also { it.seek(0, ISeekableStream.SEEK_SET) }

        override fun open(name: String): IInStream? = null

        override fun close() = joined.close()
    }
}

/**
 * 여러 파일을 순서대로 이어 붙인 하나의 스트림. 번호 볼륨은 아카이브를 바이트 단위로 자른 것이라 이어 붙이면 원래 아카이브가 된다.
 * 읽기는 볼륨 경계를 넘지 않고 그 볼륨의 남은 만큼만 돌려준다(라이브러리는 짧은 읽기를 받아 다시 읽는다).
 * 파일 크기는 연 때의 크기로 정한다 — 읽는 중에 파일이 바뀌는 것은 지원 범위 밖이며, 줄어들면 읽기 실패로 알린다.
 * @param files 이어 붙일 파일들(비어 있지 않음)
 */
internal class ConcatenatedInStream(files: List<File>) : IInStream {

    /** 연 파일들. 하나라도 열지 못하면 앞서 연 것을 닫고 실패한다. */
    private val parts: List<RandomAccessFile> = buildList {
        try {
            files.forEach { add(RandomAccessFile(it, "r")) }
        } catch (e: IOException) {
            try {
                closeAll(this)
            } catch (closeFailure: IOException) {
                e.addSuppressed(closeFailure)
            }
            throw e
        }
    }

    /** 각 파일이 이어 붙인 스트림에서 시작하는 위치. 마지막 값은 전체 길이다. */
    private val starts: LongArray = LongArray(parts.size + 1).also { starts ->
        parts.forEachIndexed { i, part -> starts[i + 1] = starts[i] + part.length() }
    }

    /** 이어 붙인 스트림의 전체 길이. */
    private val length: Long get() = starts.last()

    /** 다음에 읽을 위치. */
    private var position = 0L

    override fun seek(offset: Long, seekOrigin: Int): Long {
        val base = when (seekOrigin) {
            ISeekableStream.SEEK_SET -> 0L
            ISeekableStream.SEEK_CUR -> position
            ISeekableStream.SEEK_END -> length
            else -> throw SevenZipException("알 수 없는 seek 기준: $seekOrigin")
        }
        val target = base + offset
        if (target < 0) throw SevenZipException("스트림 앞을 가리키는 seek: $target")
        position = target
        return position
    }

    override fun read(data: ByteArray): Int {
        if (data.isEmpty() || position >= length) return 0
        val index = partIndexOf(position)
        val offsetInPart = position - starts[index]
        val count = minOf(data.size.toLong(), starts[index + 1] - position).toInt()
        val read = try {
            parts[index].run {
                seek(offsetInPart)
                read(data, 0, count)
            }
        } catch (e: IOException) {
            throw SevenZipException("볼륨을 읽지 못했습니다", e)
        }
        if (read <= 0) throw SevenZipException("볼륨이 연 뒤에 줄어들었습니다")
        position += read
        return read
    }

    override fun close() = closeAll(parts)

    /**
     * [offset] 위치를 담은 파일의 순번을 찾는다.
     * @param offset 전체 길이보다 작은 위치
     * @return 파일 순번. 길이가 0인 파일은 건너뛴다
     */
    private fun partIndexOf(offset: Long): Int = (0 until parts.size).first { offset < starts[it + 1] }
}

/**
 * [closeables]를 모두 닫는다. 하나를 닫지 못해도 나머지를 마저 닫는다.
 * @throws IOException 닫지 못한 것이 있을 때. 첫 실패에 뒤의 실패를 suppressed로 붙인다
 */
internal fun closeAll(closeables: Iterable<Closeable>) {
    var failure: IOException? = null
    for (closeable in closeables) {
        try {
            closeable.close()
        } catch (e: IOException) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
    }
    failure?.let { throw it }
}
