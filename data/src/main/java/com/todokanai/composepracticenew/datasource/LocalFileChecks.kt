package com.todokanai.composepracticenew.datasource

import android.system.ErrnoException
import android.system.Os
import com.todokanai.composepracticenew.model.FailureReason
import java.io.File
import java.io.IOException

/** 로컬 파일 작업들이 공유하는 사전 검증(디스크 공간·파티션) 함수 모음. 아카이브 항목의 위치 검증(Zip Slip)은 [ExtractionRoot]가 맡는다. */
internal object LocalFileChecks {

    /**
     * 두 파일이 동일한 파티션에 있는지 OS 레벨 디바이스 ID로 판별한다. 판별 실패(stat·경로 해석의 입출력 실패) 시 false를 반환해 copy+delete 경로를 선택한다 —
     * 그 경로가 자기 실패를 결과로 알리므로. 그 밖의 예외(구현 오류)는 잡지 않는다.
     */
    fun isSamePartition(a: File, b: File): Boolean =
        try {
            Os.stat(a.canonicalPath).st_dev == Os.stat(b.canonicalPath).st_dev
        } catch (e: ErrnoException) {
            false
        } catch (e: IOException) {
            false
        }

    /** 대상 파티션의 여유 공간 [free]가 [needed] 바이트 미만이면 공간 부족 원인을 반환하고, 충분하면 null을 반환한다. */
    fun checkDiskSpace(needed: Long, free: Long): FailureReason? =
        if (free < needed) FailureReason.NotEnoughSpace(needed, free) else null
}
