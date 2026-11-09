package com.todokanai.composepracticenew.data.ftp

/** FTP 원격 경로를 식별하는 URI 스킴 접두사. 경로 형식 규칙이 이 파일 밖으로 흩어지지 않도록 공개하지 않는다. */
private const val FTP_SCHEME = "ftp://"

/**
 * 경로가 ftp:// 형식인지 판별한다.
 * @param path 판별할 경로
 * @return ftp:// 형식이면 true
 */
internal fun isFtpUri(path: String): Boolean = path.startsWith(FTP_SCHEME)

/**
 * 서버 주소나 경로를 ftp:// 형식으로 만든다. 입력에 접두사가 있든 없든 하나만 붙는다.
 * @param location 서버 주소 또는 경로 (ftp:// 접두사 포함 가능)
 * @return ftp:// 형식의 경로
 */
internal fun ftpUriOf(location: String): String = FTP_SCHEME + ftpServerOf(location)

/**
 * 서버 주소에서 접속에 쓸 호스트 부분을 꺼낸다.
 * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
 * @return ftp:// 접두사를 뗀 주소
 */
internal fun ftpServerOf(address: String): String = address.removePrefix(FTP_SCHEME)

/** ftp://server/path 형식에서 /path 부분만 추출한다. */
internal fun extractFtpPath(path: String): String {
    val withoutScheme = path.removePrefix(FTP_SCHEME)
    val slashIndex = withoutScheme.indexOf('/')
    return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
}

/**
 * ftp:// 형식의 디렉터리 경로 아래 항목 경로의 앞부분. 항목 경로는 이 앞부분에 항목 이름을 붙인 것이다.
 * @param dirPath 디렉터리의 ftp:// 경로
 * @return 끝에 `/` 하나를 붙인 디렉터리 경로
 */
internal fun childFtpUriPrefix(dirPath: String): String = dirPath.trimEnd('/') + "/"

/**
 * ftp:// 형식의 디렉터리 경로 아래 항목의 경로를 만든다. 목록 항목의 경로도 이 규칙으로 만든다.
 * @param dirPath 디렉터리의 ftp:// 경로
 * @param name 항목 이름
 * @return 항목의 ftp:// 경로
 */
internal fun childFtpUri(dirPath: String, name: String): String = childFtpUriPrefix(dirPath) + name

/**
 * ftp:// 형식 경로의 부모 경로를 구한다. 서버 루트(ftp://host)의 부모는 없다.
 * @param path ftp:// 형식의 경로
 * @return 부모의 ftp:// 경로. 서버 루트이면 null
 */
internal fun parentFtpUri(path: String): String? {
    val withoutScheme = path.removePrefix(FTP_SCHEME)
    return if (!withoutScheme.contains('/')) null else FTP_SCHEME + withoutScheme.substringBeforeLast('/')
}

/**
 * ftp:// 형식의 항목 경로와 같은 디렉터리에 있는 다른 이름의 경로를 만든다.
 * @param path 기준 항목의 ftp:// 경로. 서버 루트가 아닌 항목이어야 한다(목록이 준 항목은 모두 부모가 있다)
 * @param name 같은 디렉터리 안의 이름
 * @return 그 이름의 ftp:// 경로
 */
internal fun siblingFtpUri(path: String, name: String): String {
    val parent = requireNotNull(parentFtpUri(path)) { "서버 루트는 같은 디렉터리의 다른 이름을 갖지 않는다: $path" }
    return childFtpUri(parent, name)
}

/**
 * FTP 경로의 상위 디렉터리 경로를 구한다. 루트의 상위는 루트다.
 * @param ftpPath /로 시작하는 FTP 경로
 * @return 상위 디렉터리의 FTP 경로
 */
internal fun parentFtpPath(ftpPath: String): String = ftpPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

/**
 * 원격 트리 항목 경로를 기준 디렉터리에 대한 상대 경로로 바꾼다.
 * @param entryPath 원격 트리 항목의 경로
 * @param rootPath 기준 원격 디렉터리 경로
 * @return 기준 디렉터리 아래 상대 경로
 */
internal fun relativeRemotePath(entryPath: String, rootPath: String): String = entryPath.removePrefix(rootPath).trimStart('/')
