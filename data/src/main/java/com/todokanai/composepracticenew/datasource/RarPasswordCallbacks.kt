package com.todokanai.composepracticenew.datasource

import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.SevenZipException
import java.io.IOException

/** 라이브러리에 넘긴 콜백이 처음 던진 예외를 보관하는 콜백. 라이브러리는 콜백의 예외를 자기 예외로 감싸므로, 호출부가 원래 예외로 알리도록 꺼낸다. */
internal interface FailureRecordingCallback {
    /** 콜백에서 처음 던진 예외. 없으면 null. */
    val failure: Throwable?
}

/**
 * 콜백 본문이 던진 예외를 처음 것 하나만 보관하고, 실패한 뒤에 오는 콜백은 같은 예외로 다시 멈추게 하는 기록기.
 * 여러 RAR 콜백이 같은 규칙으로 실패를 다루도록 한 곳에 둔다.
 */
internal class FailureRecorder {

    /** 처음 던진 예외. 없으면 null. */
    var failure: Throwable? = null
        private set

    /**
     * 이미 실패했으면 그 예외로 다시 멈추고, 아니면 [block]을 실행하며 던진 예외를 [failure]에 보관한다.
     * @param block 콜백 본문
     * @return [block]의 결과
     */
    fun <T> record(block: () -> T): T {
        failure?.let { throw it }
        return try {
            block()
        } catch (t: Throwable) {
            failure = t
            throw t
        }
    }
}

/**
 * 아카이브를 열 때 넘기는 콜백. 항목 목록까지 암호화한 아카이브를 열 때만 라이브러리가 비밀번호를 묻는다.
 * 줄 비밀번호가 없으면 열기를 멈추게 하고, 비밀번호를 물었다는 사실을 남겨 호출부가 비밀번호 때문에 열지 못했는지 가리게 한다.
 * @param password 줄 비밀번호. 없으면 null
 */
internal class RarOpenCallback(private val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {

    /** 여는 중에 라이브러리가 비밀번호를 물었는지. */
    var passwordRequested = false
        private set

    override fun cryptoGetTextPassword(): String {
        passwordRequested = true
        return password ?: throw SevenZipException("비밀번호가 필요한 압축 파일입니다")
    }

    override fun setTotal(files: Long?, bytes: Long?) {}
    override fun setCompleted(files: Long?, bytes: Long?) {}
}

/**
 * 암호화된 항목 하나를 출력 없이 풀어 후보 비밀번호를 확인하는 추출 콜백. 실제 추출과 같은 길로 풀어(시험 모드가 아님) 내용 검증까지 거친다.
 * 받은 내용은 버린다. 내용을 받을 때와 라이브러리가 진행을 알릴 때마다 비밀번호 확인 단계의 진행을 보고한다 — 진행 보고가 작업 취소를 확인하므로.
 * solid RAR은 확인할 항목에 닿기까지 앞 항목을 출력 없이 모두 푸는데, 그동안 라이브러리는 내용을 넘기지 않고 진행([setCompleted])만 알린다(#418).
 * 결과의 해석: OK면 통과, WRONG_PASSWORD(검증값이 있는 형식)면 틀림, 데이터·CRC 오류면 틀렸거나 손상(RAR4는 검증값이 없다). 그 밖의 결과는 비밀번호와 무관한 실패라 IOException으로 멈춘다.
 * @param candidate 확인할 비밀번호
 * @param reportChecking 비밀번호 확인 단계의 진행 보고. 취소됐으면 CancellationException을 던진다
 */
internal class RarPasswordCheckCallback(
    private val candidate: String,
    private val reportChecking: () -> Unit,
) : IArchiveExtractCallback, ICryptoGetTextPassword, FailureRecordingCallback {

    /** 콜백에서 던진 예외를 보관하는 기록기. */
    private val recorder = FailureRecorder()

    override val failure: Throwable? get() = recorder.failure

    /** 확인 결과. 항목을 끝까지 풀기 전에는 null. */
    var result: PasswordCheck? = null
        private set

    override fun cryptoGetTextPassword(): String = candidate

    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? = recorder.record {
        if (extractAskMode != ExtractAskMode.EXTRACT) return@record null
        ISequentialOutStream { data -> recorder.record { reportChecking(); data.size } }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode) {}

    override fun setOperationResult(extractOperationResult: ExtractOperationResult) = recorder.record {
        result = passwordCheckOf(extractOperationResult) ?: throw IOException("풀 수 없는 항목입니다 ($extractOperationResult)")
    }

    override fun setTotal(total: Long) {}
    override fun setCompleted(complete: Long) = recorder.record { reportChecking() }
}

/**
 * 암호화된 항목을 풀어 본 결과를 비밀번호 확인 결과로 바꾼다.
 * @param result 라이브러리가 알린 항목 결과
 * @return 확인 결과. 비밀번호와 무관한 실패면 null
 */
internal fun passwordCheckOf(result: ExtractOperationResult): PasswordCheck? = when (result) {
    ExtractOperationResult.OK -> PasswordCheck.ACCEPTED
    ExtractOperationResult.WRONG_PASSWORD -> PasswordCheck.WRONG
    ExtractOperationResult.DATAERROR, ExtractOperationResult.CRCERROR -> PasswordCheck.WRONG_OR_DAMAGED
    else -> null
}
