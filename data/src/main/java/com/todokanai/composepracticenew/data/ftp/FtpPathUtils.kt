package com.todokanai.composepracticenew.data.ftp

/** ftp://server/path 형식에서 /path 부분만 추출한다. */
internal fun extractFtpPath(path: String): String {
    val withoutScheme = path.removePrefix("ftp://")
    val slashIndex = withoutScheme.indexOf('/')
    return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
}
