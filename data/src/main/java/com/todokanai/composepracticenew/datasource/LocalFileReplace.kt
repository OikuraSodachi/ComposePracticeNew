package com.todokanai.composepracticenew.datasource

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 같은 파일 시스템 안에서 [src] 파일을 [target] 자리로 rename해, 그 자리의 파일을 한 번에 대신한다.
 * 로컬 작업이 rename으로 기존 파일을 교체하는 길은 이것 하나다(#432) — `REPLACE_EXISTING`은 Unix 구현이 대상을 먼저 지운 뒤 rename하므로,
 * rename이 실패하면 실패한 작업이 대상만 지운 채 끝나고, 지운 뒤 옮기기 전에 그 자리에 생긴 항목과 다시 겹친다.
 * `ATOMIC_MOVE`의 rename은 대상을 교체하거나(Unix rename, Windows MoveFileEx) 아무것도 바꾸지 않고 실패한다.
 * [target]이 폴더이면 실패한다.
 * @param src 옮길 파일
 * @param target [src]가 대신할 자리. [src]와 같은 파일 시스템에 있어야 한다
 * @throws java.io.IOException 옮기지 못했을 때. 다른 파일 시스템이면 [java.nio.file.AtomicMoveNotSupportedException]
 */
internal fun replaceFileAtomically(src: File, target: File) {
    Files.move(src.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
}
