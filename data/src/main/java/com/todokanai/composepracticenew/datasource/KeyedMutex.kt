package com.todokanai.composepracticenew.datasource

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 키마다 따로 잠그는 코루틴 잠금. 같은 키의 [withLock]은 차례로 실행하고, 다른 키끼리는 서로 기다리지 않는다.
 * 기다리는 동안 취소되면 기다림을 끝낸다([Mutex.lock]과 같다). 쓰는 곳이 없어진 키는 지워, 다룬 키가 쌓이지 않는다.
 */
internal class KeyedMutex<K : Any> {

    /**
     * 한 키의 잠금과 그 잠금을 쥐었거나 기다리는 수.
     * @param mutex 그 키의 잠금
     */
    private class Entry(val mutex: Mutex = Mutex()) {
        /** 이 잠금을 쥐었거나 기다리는 호출 수. 0이 되면 키를 지운다. [entries]의 잠금 안에서만 바꾼다. */
        var users = 0
    }

    /** 쓰는 곳이 있는 키의 잠금. 이 맵 자신을 잠그고 다룬다. */
    private val entries = HashMap<K, Entry>()

    /**
     * [key]의 잠금을 쥔 채 [block]을 실행한다.
     * @param key 잠글 키
     * @param block 잠금 안에서 실행할 일
     * @return [block]의 결과
     */
    suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        // 기다리기 전에 수를 센다 — 기다리는 호출이 있는 동안 다른 호출이 키를 지우고 새 잠금을 만들지 않도록
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) { if (--entry.users == 0) entries.remove(key) }
        }
    }
}
