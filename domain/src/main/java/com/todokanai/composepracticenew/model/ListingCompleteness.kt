package com.todokanai.composepracticenew.model

/**
 * 조회한 목록이 폴더의 항목을 빠짐없이 담았다고 확인됐는지 여부.
 * 확인되지 않은 목록은 화면에는 그대로 보여 주지만, 목적지의 기존 항목을 판단하는 데(이름 충돌 판정) 그대로 믿으면 안 된다.
 */
enum class ListingCompleteness {
    /** 저장소가 목록을 끝까지 돌려줬다. 비어 있으면 실제로 빈 폴더다. */
    COMPLETE,

    /**
     * 저장소 응답만으로는 빈 폴더인지 일시 거부인지 알 수 없어 빈 목록으로 보여 주는 목록이다. 실제로는 항목이 있을 수 있다.
     * 예: FTP LIST에 빈 폴더 대신 "450"을 보내는 서버 — 같은 코드가 "450 File busy" 같은 일시 거부이기도 하다.
     */
    UNCONFIRMED,
}
