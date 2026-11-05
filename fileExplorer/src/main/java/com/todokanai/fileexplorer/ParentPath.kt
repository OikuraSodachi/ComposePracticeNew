package com.todokanai.fileexplorer

/**
 * '/' 구분자 기준으로 path의 부모 경로를 반환한다. 루트이면 null을 반환한다.
 * @param path 부모를 조회할 경로
 * @return 부모 경로 또는 null
 */
fun defaultParentPath(path: String): String? =
    path.substringBeforeLast('/').takeIf { it.isNotEmpty() && it != path }
