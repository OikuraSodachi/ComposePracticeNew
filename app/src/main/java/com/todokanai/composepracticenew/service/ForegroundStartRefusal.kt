package com.todokanai.composepracticenew.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 시스템이 포그라운드 서비스의 시작을 거부한 예외인지 판별한다.
 * 거부는 두 곳에서 난다 — `Context.startForegroundService`(Android 12+ 백그라운드 시작 제한)와 `Service.startForeground`(같은 제한, Android 15 실행 시간 한도 소진).
 * 그 밖의 IllegalStateException은 구현 오류이므로 거부로 보지 않는다.
 * @return 시작 거부이면 true
 */
internal fun IllegalStateException.isForegroundStartRefusal(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && this is ForegroundServiceStartNotAllowedException

/**
 * 포그라운드 서비스를 시작한다. 시스템이 시작을 거부하면 서비스를 띄우지 않고 false를 돌려준다(거부가 아닌 예외는 그대로 던진다).
 * @param intent 시작할 서비스의 Intent
 * @return 시작 요청이 받아들여졌으면 true, 시스템이 거부했으면 false
 */
internal fun Context.startForegroundServiceUnlessRefused(intent: Intent): Boolean =
    try {
        startForegroundService(intent)
        true
    } catch (e: IllegalStateException) {
        if (!e.isForegroundStartRefusal()) throw e
        false
    }
