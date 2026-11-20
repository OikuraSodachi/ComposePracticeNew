package com.todokanai.composepracticenew.datasource

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * [entry](파일 또는 폴더)에 쓰인 내용을 저장 장치에 기록할 때까지 기다린다(fsync).
 * 닫은 출력의 내용은 OS 캐시에만 있을 수 있어, 원본을 지우기 전에 복사본을 장치에 남기려고 쓴다(#430).
 * 다시 열어 기록해도 된다 — fsync는 그 설명자로 쓴 내용이 아니라 파일에 쓰인 내용 전체를 기록한다.
 * 폴더는 그 안의 이름(새로 만든 항목)을 기록한다. 폴더를 열 수 없는 OS(Windows)에서는 폴더에 대해 실패한다.
 * @param entry 기록할 파일이나 폴더
 * @throws java.io.IOException 열거나 기록하지 못했을 때
 */
internal fun forceToDisk(entry: File) {
    val mode = if (entry.isDirectory) StandardOpenOption.READ else StandardOpenOption.WRITE
    FileChannel.open(entry.toPath(), mode).use { it.force(true) }
}
