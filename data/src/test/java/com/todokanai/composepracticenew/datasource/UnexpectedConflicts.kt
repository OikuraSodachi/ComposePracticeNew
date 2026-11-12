package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.repository.WriteConflictResolver

/** 충돌이 오면 테스트를 실패시키는 충돌 판정자. 충돌이 없어야 하는 테스트가 쓴다. */
internal val UnexpectedConflictResolver = WriteConflictResolver { throw AssertionError("예상하지 못한 충돌: $it") }

/** 충돌이 오면 테스트를 실패시키는 동기 질문 함수. 충돌이 없어야 하는 테스트가 쓴다. */
internal val UnexpectedConflictAsk: (WriteConflict) -> ConflictChoice = { throw AssertionError("예상하지 못한 충돌: $it") }
