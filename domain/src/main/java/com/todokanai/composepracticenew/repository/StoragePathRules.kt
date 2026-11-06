package com.todokanai.composepracticenew.repository

/**
 * 저장소별 경로 규칙(부모 경로·표시 이름)의 계약. 구분자처럼 경로를 쪼개는 규칙은 구현체만 알며,
 * 탐색 상태([FileNavigatorRepository])와 나눠 두어 경로 관계만 필요한 쪽이 탐색 상태에 묶이지 않게 한다.
 */
interface StoragePathRules {
    /**
     * 주어진 경로의 부모 경로를 반환한다. 루트이면 null을 반환한다.
     * 부모는 [path]보다 위의 경로여야 한다 — 호출부가 이 함수를 따라 루트(null)까지 올라가기 때문이다.
     */
    fun getParentPath(path: String): String?
    /** 주어진 디렉터리 경로의 표시 이름을 반환한다. 경로에서 이름을 꺼내는 규칙은 저장소마다 다르므로 구현체가 정한다. */
    fun displayNameOf(path: String): String
}
