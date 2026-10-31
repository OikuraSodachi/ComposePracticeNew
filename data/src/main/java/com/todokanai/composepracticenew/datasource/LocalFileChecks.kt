package com.todokanai.composepracticenew.datasource

import android.system.Os
import java.io.File

/** 로컬 파일 작업들이 공유하는 사전 검증(디스크 공간·파티션·Zip Slip) 함수 모음. */
internal object LocalFileChecks {

    /** 두 파일이 동일한 파티션에 있는지 OS 레벨 디바이스 ID로 판별한다. 판별 실패 시 false를 반환해 copy+delete 경로를 선택한다. */
    fun isSamePartition(a: File, b: File): Boolean = runCatching {
        Os.stat(a.canonicalPath).st_dev == Os.stat(b.canonicalPath).st_dev
    }.getOrDefault(false)

    /** 대상 파티션의 여유 공간 [free]가 [needed] 바이트 미만이면 오류 메시지를 반환하고, 충분하면 null을 반환한다. */
    fun checkDiskSpace(needed: Long, free: Long): String? {
        return if (free < needed)
            "디스크 공간 부족: 필요 ${needed / 1024} KB, 여유 ${free / 1024} KB"
        else null
    }

    /** [file]의 정규화 경로가 [root] 하위에 있는지 확인한다. Zip Slip 방지용. */
    fun isUnderRoot(file: File, root: File): Boolean {
        val rootPath = root.canonicalPath.let {
            if (it.endsWith(File.separator)) it else it + File.separator
        }
        return file.canonicalFile.path.startsWith(rootPath)
    }
}
