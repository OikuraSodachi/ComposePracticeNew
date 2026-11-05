package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.IoResult

/**
 * 진행 표시가 있는 파일 작업이 시작하기 전에 받는 프로세스 보호. 보호를 받은 작업만 시작한다.
 * 보호를 주는 수단(포그라운드 서비스 등)은 구현이 정한다.
 */
interface OperationProtection {
    /**
     * 작업을 시작하기 전에 보호를 요청하고, 보호를 받거나 거부될 때까지 기다린다.
     * 보호는 시스템의 실행 시간 한도까지이며, 한도 이후의 보호는 지원 범위 밖이다.
     * @return 보호를 받았으면 [IoResult.Done], 거부됐으면 작업을 시작하지 않았다는 메시지를 담은 실패
     */
    suspend fun acquire(): IoResult<Unit>
}
