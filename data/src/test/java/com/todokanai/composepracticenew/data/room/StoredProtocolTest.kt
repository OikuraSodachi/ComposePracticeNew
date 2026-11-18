package com.todokanai.composepracticenew.data.room

import org.junit.Assert.assertEquals
import org.junit.Test

/** 컬럼 기본값·마이그레이션이 쓰는 저장 값이 Room이 enum으로 저장하는 값과 같은지 검증하는 단위 테스트. */
class StoredProtocolTest {

    @Test
    fun `FTP 기본값 상수는 Room이 저장하는 FTP 상수 이름과 같다`() {
        assertEquals(StoredProtocol.FTP.name, PROTOCOL_FTP)
    }
}
