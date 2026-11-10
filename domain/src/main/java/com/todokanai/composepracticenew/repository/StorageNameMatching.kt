package com.todokanai.composepracticenew.repository

/**
 * 저장소가 같은 항목으로 다룰 수 있는 이름끼리 같은 비교 값을 돌려주는 규칙.
 * 대소문자와 Unicode 정규형을 어떻게 다루는지는 저장소(파일 시스템)의 성질이므로 이름이 놓인 위치로 규칙을 고른다. 이름 충돌 판정은 이 값으로만 이름을 비교한다.
 */
fun interface StorageNameMatching {
    /**
     * [location]의 저장소에서 [name]이 다른 이름과 같은 항목으로 다뤄질 수 있는지 비교할 값을 구한다.
     * 저장소의 규칙을 알 수 없으면 같은 항목이 될 수 있는 이름을 모두 같은 값으로 묶는다 — 다른 이름으로 잘못 보면 확인 없이 덮어쓰기 때문이다.
     * @param name 비교할 항목 이름
     * @param location 이름이 놓인 폴더의 경로
     * @return 비교 값. 두 이름의 값이 같으면 저장소가 같은 항목으로 다룰 수 있다
     */
    fun matchKeyOf(name: String, location: String): String
}
