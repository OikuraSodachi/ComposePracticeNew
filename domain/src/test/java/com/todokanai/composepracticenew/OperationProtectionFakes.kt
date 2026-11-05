package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.operation.OperationProtection

/** 언제나 바로 보호를 주는 테스트용 작업 보호. 보호를 다루지 않는 테스트가 쓴다. */
internal object GrantedProtection : OperationProtection {
    override suspend fun acquire(): IoResult<Unit> = IoResult.Done
}
