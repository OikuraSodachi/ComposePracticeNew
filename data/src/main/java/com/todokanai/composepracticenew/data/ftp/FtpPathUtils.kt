package com.todokanai.composepracticenew.data.ftp

/** FTP 원격 경로를 식별하는 URI 스킴 접두사. FTP 프로토콜 지식은 data 모듈의 FTP 구현 안에만 둔다. */
internal const val FTP_SCHEME = "ftp://"

/** ftp://server/path 형식에서 /path 부분만 추출한다. */
internal fun extractFtpPath(path: String): String {
    val withoutScheme = path.removePrefix(FTP_SCHEME)
    val slashIndex = withoutScheme.indexOf('/')
    return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
}

/**
 * 원격 트리 항목 경로를 기준 디렉터리에 대한 상대 경로로 바꾼다.
 * @param entryPath 원격 트리 항목의 경로
 * @param rootPath 기준 원격 디렉터리 경로
 * @return 기준 디렉터리 아래 상대 경로
 */
internal fun relativeRemotePath(entryPath: String, rootPath: String): String = entryPath.removePrefix(rootPath).trimStart('/')
