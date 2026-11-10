package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileLaunchResult

/** 로컬 파일을 그 형식을 여는 다른 앱으로 연다. 구현은 플랫폼이 맡는다. */
interface FileLauncher {
    /**
     * path의 파일을 연다.
     * @param path 열 파일의 경로
     * @return 넘겼으면 [FileLaunchResult.Launched], 열지 못했으면 그 이유
     */
    fun launch(path: String): FileLaunchResult
}
