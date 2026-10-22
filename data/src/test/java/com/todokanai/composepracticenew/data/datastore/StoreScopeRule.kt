package com.todokanai.composepracticenew.data.datastore

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * [PreferencesStore]의 소비자 코루틴을 돌릴 스코프를 만들고 테스트가 끝나면 정리하는 JUnit 규칙.
 * 소비자는 끝나지 않으므로 runTest의 자식이 아닌 별도 스코프로 두되 같은 스케줄러를 써서 advanceUntilIdle 등으로 함께 진행시킨다.
 * 별도 스코프의 예외는 runTest로 전파되지 않으므로, 잡히지 않은 예외를 기록해 두었다가 테스트가 끝날 때 실패로 드러낸다.
 */
class StoreScopeRule : TestRule {
    /** 이번 테스트에서 만든 스코프. 테스트 종료 시 취소한다. */
    private val scopes = mutableListOf<CoroutineScope>()
    /** 소비자 코루틴에서 잡히지 않은 예외. */
    private val uncaught = mutableListOf<Throwable>()

    /**
     * [test]의 스케줄러를 공유하고 잡히지 않은 예외를 기록하는 스코프를 만든다.
     * @param test 진행을 함께할 runTest의 스코프
     * @return PreferencesStore에 넘길 스코프
     */
    fun scopeFor(test: TestScope): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler) + CoroutineExceptionHandler { _, e -> uncaught += e })
            .also { scopes += it }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                scopes.forEach { it.cancel() }
            }
            uncaught.firstOrNull()?.let { throw AssertionError("소비자 코루틴에서 잡히지 않은 예외가 발생했습니다", it) }
        }
    }
}
