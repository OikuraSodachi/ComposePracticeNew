package com.todokanai.composepracticenew.model

/**
 * 화면에 알리기를 기다리는 알림 하나.
 * @param id 알림마다 다른 식별자. 화면이 이 알림을 꺼낼 때([com.todokanai.composepracticenew.repository.NoticeInbox.claim]) 쓴다
 * @param content 알릴 내용
 */
data class PendingNotice<out T>(val id: Long, val content: T)
