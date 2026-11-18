package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.ftpUriOf
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteStorageItem

/**
 * 연결한 FTP 서버의 루트 URI를 만들어 원격 탐색 저장소를 그곳으로 옮기는 RemoteRootNavigator 구현체.
 * 루트 URI와 그 URI를 받는 [FtpFileSystem]이 같은 경로 규칙(`ftpUriOf`)을 쓰고,
 * FTP 파일 시스템은 권한을 이유로 이동을 거부하지 않으므로(권한 문제는 목록 조회 실패로 알린다) 거부는 원격 탐색 저장소의 배선 오류다.
 * @param nav 원격 화면의 탐색 저장소 — FTP 파일 시스템을 포함한 원격 화면 파일 시스템 집합으로 만든 것
 */
internal class FtpRootNavigator(private val nav: FileNavigatorRepository) : RemoteRootNavigator {

    /** @throws IllegalStateException 원격 탐색 저장소가 루트 URI로의 이동을 거부했을 때(배선 오류) */
    override suspend fun navigateToRoot(item: RemoteStorageItem) {
        val root = ftpUriOf(item.address)
        check(nav.navigate(root) == NavigateResult.Navigated) { "연결한 서버의 루트 경로를 원격 탐색 저장소가 다루지 않는다(배선 오류): $root" }
    }
}
