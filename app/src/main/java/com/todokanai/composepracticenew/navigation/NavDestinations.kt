package com.todokanai.composepracticenew.navigation

/** 앱 내 화면 전환에 사용되는 navigation route 상수 모음. */
object NavDestinations {
    /** 로컬 저장소 볼륨과 원격 서버를 고르는 저장소 화면. */
    const val STORAGE = "storage"
    /** 로컬·원격 파일 목록을 묶는 그래프. 전송 조정 ViewModel이 이 그래프에 스코프된다. */
    const val FILE_BROWSER_GRAPH = "fileBrowserGraph"
    /** 로컬 파일 목록 화면. */
    const val FILE_LIST = "fileList"
    /** 원격 파일 목록 화면. */
    const val REMOTE_FILE_LIST = "remoteFileList"
}
