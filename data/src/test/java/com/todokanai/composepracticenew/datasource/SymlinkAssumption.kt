package com.todokanai.composepracticenew.datasource

import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * [link]에 [target]을 가리키는 심볼릭 링크를 만든다. 링크를 만들 수 없는 환경(권한 없는 Windows 등)이면 테스트를 건너뛴다.
 * @param link 만들 링크 경로
 * @param target 링크가 가리킬 경로
 */
internal fun assumeSymlink(link: File, target: File) {
    val created = try {
        Files.createSymbolicLink(link.toPath(), target.toPath())
        true
    } catch (e: IOException) {
        false
    } catch (e: UnsupportedOperationException) {
        false
    }
    assumeTrue(created)
}
