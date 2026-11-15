package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectFailureAlert

/** 원격 연결 실패를 화면이 꺼내 알리게 한다. 실패한 순간 받을 화면이 없어도 알림을 잃지 않도록 알림함으로 둔다. 넣는 쪽 계약은 [ConnectFailureAlertReporter]다. */
interface ConnectFailureAlertRepository : NoticeInbox<ConnectFailureAlert>
