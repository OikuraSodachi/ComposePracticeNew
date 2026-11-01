package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.todokanai.composepracticenew.model.PreferenceFailure
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 손상된 설정 파일을 빈 Preferences로 바꾸는 핸들러를 제공하고, 교체 파일이 실제로 쓰인 뒤에만 초기화를 알린다.
 * 핸들러는 교체 값을 만들 뿐이고 파일 쓰기는 그 뒤 DataStore가 하며, 쓰기가 끝났음을 알리는 콜백은 없다.
 * DataStore는 교체 파일을 쓰지 못하면 손상 예외를 다시 던지고, 쓴 뒤에야 읽기·편집을 성공으로 돌려준다(datastore-core 1.1.1 `DataStoreImpl.readDataOrHandleCorruption`).
 * 그래서 핸들러가 불린 뒤 처음 성공한 읽기·편집([confirmIfReplaced])에서 알린다.
 * @param failures 초기화를 알릴 대기열
 */
@Singleton
class CorruptionReset @Inject constructor(
    private val failures: PreferenceFailureChannel
) {

    /** 핸들러가 교체 값을 만들었지만 아직 초기화를 알리지 않았는지. 교체 쓰기가 실패해 핸들러가 여러 번 불려도 알림은 한 번이 되도록 둔다. */
    private val replacing = AtomicBoolean(false)

    /** DataStore에 넘길 손상 핸들러. 기본 핸들러는 예외를 다시 던지기만 해 읽기·쓰기가 영구히 실패하므로, 빈 Preferences로 교체한다. */
    val handler: ReplaceFileCorruptionHandler<Preferences> = ReplaceFileCorruptionHandler {
        replacing.set(true)
        emptyPreferences()
    }

    /** DataStore 읽기·편집이 성공했을 때 부른다. 핸들러가 교체 값을 만든 뒤라면 교체 파일이 쓰였다는 뜻이므로 [PreferenceFailure.RESET]을 한 번 알린다. */
    fun confirmIfReplaced() {
        if (replacing.compareAndSet(true, false)) failures.report(PreferenceFailure.RESET)
    }
}
