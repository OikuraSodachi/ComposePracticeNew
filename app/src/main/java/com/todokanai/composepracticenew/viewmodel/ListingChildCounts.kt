package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 목록 조회 결과 하나에 속한 폴더들의 하위 항목 수. 폴더 행이 구독할 때 세고, 센 결과는 이 목록이 표시되는 동안 다시 쓴다.
 * 새 조회 결과(이동·새로고침·작업 후 갱신)에는 새 인스턴스를 쓰므로 그 목록의 폴더는 다시 센다 —
 * 폴더의 수정 시각은 해상도(FAT은 2초)가 거칠어, 같은 시각이라도 내용이 바뀌었을 수 있으므로 목록을 넘어 결과를 재사용하지 않는다.
 * 구독이 취소되면(행이 화면을 벗어남) 기다리던 세기도 취소되고 결과를 남기지 않는다.
 * 메인 스레드에서만 쓴다 — 결과 보관은 동기화하지 않는다.
 * @param countChildren 폴더 하나의 바로 아래 항목 수를 세는 함수
 * @param permits 동시에 세는 폴더 수를 제한하는 세마포어. 빠르게 스크롤할 때 디렉터리 읽기가 한꺼번에 몰리지 않도록 목록들이 함께 쓴다
 */
class ListingChildCounts(
    private val countChildren: suspend (FileHolderItem) -> IoResult<Int>,
    private val permits: Semaphore
) {

    /** 이 목록에서 다 센 폴더 경로별 표시 상태. */
    private val results = mutableMapOf<String, ChildCountInfo>()

    /**
     * [directory]의 지금 알고 있는 표시 상태를 돌려준다. 세지 않는다.
     * @param directory 이 목록의 폴더 항목
     * @return 다 셌으면 그 결과, 아니면 [ChildCountInfo.Loading]
     */
    fun currentOf(directory: FileHolderItem): ChildCountInfo = results[directory.path] ?: ChildCountInfo.Loading

    /**
     * [directory]의 하위 항목 수를 내보낸다. 이 목록에서 이미 셌으면 그 결과를, 아니면 세어 결과를 한 번 내보낸다.
     * @param directory 이 목록의 폴더 항목
     * @return 표시 상태를 한 번 내보내는 Flow
     */
    fun countOf(directory: FileHolderItem): Flow<ChildCountInfo> = flow {
        val count = results[directory.path] ?: permits.withPermit { ChildCountInfo.from(countChildren(directory)) }
        results[directory.path] = count
        emit(count)
    }
}
