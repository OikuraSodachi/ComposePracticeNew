package com.todokanai.composepracticenew.navigation

import android.net.Uri
import com.todokanai.composepracticenew.viewmodel.ArchiveViewViewModel

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
    /** 압축 파일 내용 보기 화면. 압축 파일 경로를 인자로 받는다. */
    const val ARCHIVE_VIEW = "archiveView/{${ArchiveViewViewModel.ARG_ARCHIVE_PATH}}"

    /**
     * [archivePath] 압축 파일의 내용 보기 화면으로 가는 route를 만든다. 경로의 '/'가 route 구분으로 읽히지 않도록 인코딩한다.
     * @param archivePath 압축 파일 경로
     * @return 이동할 route
     */
    fun archiveView(archivePath: String): String = "archiveView/${Uri.encode(archivePath)}"
}
