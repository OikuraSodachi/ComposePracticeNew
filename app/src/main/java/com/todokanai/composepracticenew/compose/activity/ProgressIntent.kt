package com.todokanai.composepracticenew.compose.activity

import android.content.Intent
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_INSTANCE_ID
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_PROCESS_TOKEN
import com.todokanai.composepracticenew.tools.NotificationProcessToken

/**
 * 진행 알림 클릭 intent가 가리키는 작업.
 * @param operationTypeId 작업의 FileOperationType.id
 * @param instanceId 작업 인스턴스 ID
 */
internal data class ProgressDialogRequest(val operationTypeId: Int, val instanceId: Int)

/**
 * [intent]가 이 프로세스가 만든 진행 알림의 클릭 intent이면 그 작업을 돌려준다.
 * @param intent Activity가 받은 intent
 * @return 가리키는 작업. 진행 알림 intent가 아니거나 다른 프로세스가 만든 것이면 null
 */
internal fun progressDialogRequestOf(intent: Intent?): ProgressDialogRequest? {
    if (intent == null) return null
    return progressDialogRequestOf(
        processToken = intent.getStringExtra(EXTRA_PROCESS_TOKEN),
        operationTypeId = intent.getIntExtra(EXTRA_ACTION_KEY, MISSING_EXTRA),
        instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, MISSING_EXTRA)
    )
}

/**
 * 진행 알림 intent에서 꺼낸 값으로 가리키는 작업을 정한다.
 * @param processToken intent를 만든 프로세스의 값
 * @param operationTypeId intent의 작업 종류 id. 없으면 [MISSING_EXTRA]
 * @param instanceId intent의 작업 인스턴스 ID. 없으면 [MISSING_EXTRA]
 * @return 가리키는 작업. 값이 빠졌거나 다른 프로세스가 만든 intent이면 null
 */
internal fun progressDialogRequestOf(processToken: String?, operationTypeId: Int, instanceId: Int): ProgressDialogRequest? {
    // 이전 프로세스가 남긴 알림의 intent는 그 프로세스의 작업 ID를 담으므로, 이 프로세스의 같은 ID 작업을 가리키지 않도록 무시한다
    if (!NotificationProcessToken.isFromThisProcess(processToken)) return null
    if (operationTypeId == MISSING_EXTRA || instanceId == MISSING_EXTRA) return null
    return ProgressDialogRequest(operationTypeId, instanceId)
}

/** intent에 정수 extra가 없을 때 받는 기본값. */
internal const val MISSING_EXTRA = -1

/**
 * onCreate가 받은 intent가 새로 전달된 것인지 판정한다.
 * 저장 상태가 있으면(회전 등 구성 변경, 프로세스 복원) 또는 최근 앱에서 다시 실행했으면 처음 실행한 intent를 다시 받은 것이다.
 * @param hasSavedState onCreate의 savedInstanceState가 null이 아닌지
 * @param intentFlags onCreate가 받은 intent의 flags
 * @return 새로 전달된 intent이면 true
 */
internal fun isNewLaunchIntent(hasSavedState: Boolean, intentFlags: Int): Boolean =
    !hasSavedState && (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
