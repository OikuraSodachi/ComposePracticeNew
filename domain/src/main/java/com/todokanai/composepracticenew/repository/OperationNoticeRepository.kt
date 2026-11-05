package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNotice

/**
 * 파일 작업의 실패·거부·완료 메시지를 화면이 꺼내 알릴 때까지 보관하는 알림함. 메시지를 넣는 쪽 계약은 [OperationNoticeReporter]다.
 * 작업의 진행 상태는 [ProgressRepository]가 맡는다 — 실행 전 거부처럼 진행과 무관한 메시지도 여기로 온다.
 */
interface OperationNoticeRepository : NoticeInbox<OperationNotice>
