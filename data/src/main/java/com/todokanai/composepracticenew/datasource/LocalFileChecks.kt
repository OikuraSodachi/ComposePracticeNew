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

    /**
     * 아카이브 항목 [entryName]을 [root] 아래 어디에 풀지 정한다. 추출과 충돌 판정이 같은 규칙으로 위치를 정하도록 한 곳에 둔다.
     * @param root 추출 대상 루트 디렉터리
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 풀 위치. [root] 밖을 가리키면(Zip Slip) null
     */
    fun entryTargetOf(root: File, entryName: String): File? = root.resolve(entryName).takeIf { isUnderRoot(it, root) }

    /**
     * 아카이브 항목 [entryName]을 풀면 [root] 바로 아래에 생기는(또는 덮어쓰는) 이름을 구한다. 위치는 [entryTargetOf]로 정한다.
     * @param root 추출 대상 루트 디렉터리
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return [root] 바로 아래 이름. [root] 밖을 가리켜 풀지 않는 항목이면 null
     */
    fun topLevelNameOf(root: File, entryName: String): String? =
        entryTargetOf(root, entryName)?.let { root.canonicalFile.toPath().relativize(it.canonicalFile.toPath()).getName(0).toString() }
}
