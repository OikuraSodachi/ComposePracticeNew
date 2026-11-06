package com.todokanai.fileexplorer

/**
 * '/' 구분자 기준으로 path의 표시 이름을 반환한다.
 * @param path 이름을 구할 경로
 * @return 마지막 '/' 뒤 부분. 비어 있으면(경로가 '/'로 끝나면) 경로 전체
 */
fun defaultDisplayName(path: String): String = path.substringAfterLast('/').ifEmpty { path }
