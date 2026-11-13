package com.todokanai.composepracticenew.data.ftp

/** [path]의 원격 디렉터리를 마지막 경로 조각을 이름으로 하는 삭제 대상으로 만든다. */
internal fun directoryTarget(path: String) = RemoteDeletionTarget(path, path.substringAfterLast('/'), isDirectory = true)

/** [path]의 원격 파일을 마지막 경로 조각을 이름으로 하는 삭제 대상으로 만든다. */
internal fun fileTarget(path: String) = RemoteDeletionTarget(path, path.substringAfterLast('/'), isDirectory = false)
