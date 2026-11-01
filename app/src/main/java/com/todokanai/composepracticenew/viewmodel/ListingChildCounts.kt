package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 목록 조회 결과 하나에 속한 폴더들의 하위 항목 수. 폴더 행이 구독할 때 세고, 센 결과는 이 목록이 표시되는 동안 다시 쓴다.
 * 새 조회 결과(이동·새로고침·작업 후 갱신)에는 새 인스턴스를 쓰므로 그 목록의 폴더는 다시 센다 —
 * 폴더의 수정 시각은 해상도(FAT은 2초)가 거칠어, 같은 시각이라도 내용이 바뀌었을 수 있으므로 목록을 넘어 결과를 재사용하지 않는다.
 * 세기는 구독이 아니라 [scope]에서 돌며, 같은 폴더를 여러 구독이 기다려도 한 번만 센다.
 * 디렉터리 읽기는 중간에 끊을 수 없으므로, 읽기를 시작한 세기는 구독이 모두 떠나도(행이 화면을 벗어남) 끝까지 세어 결과를 남긴다.
 * 아직 [permits]를 기다리던 세기는 구독이 모두 떠나면 취소한다 — 빠르게 스크롤해 지나친 폴더까지 세지 않도록.
 * 메인 스레드에서만 쓴다 — 결과 보관은 동기화하지 않는다.
 * @param countChildren 폴더 하나의 바로 아래 항목 수를 세는 함수
 * @param permits 동시에 세는 폴더 수를 제한하는 세마포어. 빠르게 스크롤할 때 디렉터리 읽기가 한꺼번에 몰리지 않도록 목록들이 함께 쓴다
 * @param scope 세기를 실행하는 범위. 메인 스레드에서 실행해야 한다
 */
class ListingChildCounts(
    private val countChildren: suspend (FileHolderItem) -> IoResult<Int>,
    private val permits: Semaphore,
    private val scope: CoroutineScope
) {

    /** 이 목록에서 다 센 폴더 경로별 표시 상태. */
    private val results = mutableMapOf<String, ChildCountInfo>()

    /** 경로별로 진행 중인 세기. 다 세거나 취소하면 뺀다. */
    private val inFlight = mutableMapOf<String, Counting>()

    /** 디렉터리 읽기를 시작해 더는 취소하지 않는 폴더 경로. */
    private val reading = mutableSetOf<String>()

    /**
     * [directory]의 지금 알고 있는 표시 상태를 돌려준다. 세지 않는다.
     * @param directory 이 목록의 폴더 항목
     * @return 다 셌으면 그 결과, 아니면 [ChildCountInfo.Loading]
     */
    fun currentOf(directory: FileHolderItem): ChildCountInfo = results[directory.path] ?: ChildCountInfo.Loading

    /**
     * [directory]의 하위 항목 수를 내보낸다. 이 목록에서 이미 셌으면 그 결과를, 세는 중이면 그 세기를 기다린 결과를, 아니면 세어 결과를 한 번 내보낸다.
     * @param directory 이 목록의 폴더 항목
     * @return 표시 상태를 한 번 내보내는 Flow
     */
    fun countOf(directory: FileHolderItem): Flow<ChildCountInfo> = flow {
        val counted = results[directory.path]
        if (counted != null) {
            emit(counted)
            return@flow
        }
        val counting = inFlight.getOrPut(directory.path) { Counting(scope.async(start = CoroutineStart.LAZY) { count(directory) }) }
        counting.subscribers++
        val count = try {
            counting.result.await()
        } finally {
            leave(directory.path, counting)
        }
        emit(count)
    }

    /**
     * [directory]를 세어 결과를 남긴다. 차례가 오면 더는 취소하지 않는 읽기로 표시한다.
     * @param directory 셀 폴더 항목
     * @return 센 표시 상태
     */
    private suspend fun count(directory: FileHolderItem): ChildCountInfo {
        val count = permits.withPermit {
            reading += directory.path
            ChildCountInfo.from(countChildren(directory))
        }
        results[directory.path] = count
        inFlight.remove(directory.path)
        reading -= directory.path
        return count
    }

    /**
     * [counting]을 기다리던 구독 하나가 떠난다. 남은 구독이 없고 아직 읽기를 시작하지 않았으면 그 세기를 취소한다.
     * @param path 세던 폴더 경로
     * @param counting 그 경로의 진행 중인 세기
     */
    private fun leave(path: String, counting: Counting) {
        counting.subscribers--
        if (counting.subscribers > 0 || path in reading || counting.result.isCompleted) return
        counting.result.cancel()
        inFlight.remove(path)
    }

    /**
     * 폴더 하나의 진행 중인 세기와 그 결과를 기다리는 구독 수.
     * @param result 센 표시 상태. 첫 구독이 기다릴 때 시작한다
     */
    private class Counting(val result: Deferred<ChildCountInfo>) {
        /** 이 세기를 기다리는 구독 수. */
        var subscribers = 0
    }
}
