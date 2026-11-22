package com.todokanai.composepracticenew.tools

/** 프로세스가 시작될 때 이전 프로세스가 남긴 파일 작업 진행 알림을 정리하는 인터페이스. */
interface LeftoverProgressNotifications {
    /**
     * 이전 프로세스가 남긴 진행 알림을 중단 알림으로 바꾼다. 프로세스가 시작될 때, 이 프로세스가 진행 알림을 띄우기 전에 한 번 부른다 —
     * 그때 남아 있는 진행 알림은 모두 이미 죽은 프로세스의 작업이다(#300).
     */
    fun interruptLeftoverProgress()
}
