package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry

/**
 * 디렉터리 목록 조회 결과.
 * @param entries 읽은 항목
 * @param unreadableCount 저장소가 보냈지만 읽지 못해 [entries]에서 뺀 항목(FTP 목록에서 해석하지 못한 줄) 수. 0이 아니면 빠진 항목이 있어 이 목록으로 이름 충돌을 판정하지 않는다
 */
data class ListedDirectory(val entries: List<FileEntry>, val unreadableCount: Int)
