package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalArchiveExtraction
import com.todokanai.composepracticenew.datasource.LocalEntryMutator
import com.todokanai.composepracticenew.datasource.LocalTreeTransfer
import com.todokanai.composepracticenew.datasource.LocalZipCompressor
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 로컬 파일 작업 저장소가 "디렉터리 + 이름"으로 받은 압축 파일 목적지를 그 디렉터리 아래 경로로 만드는지 검증하는 단위 테스트(#228). */
class FileActionRepositoryPathTest {

    /** 압축할 파일과 압축 파일을 두는 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("file-action-path-test").toFile()
    /** 검증 대상. */
    private val repo = FileActionRepositoryImpl(LocalTreeTransfer(), LocalZipCompressor(), LocalArchiveExtraction(emptySet()), LocalEntryMutator())

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `압축 파일은 부모 디렉터리 아래 받은 이름으로 만든다`() {
        val source = File(dir, "a.txt").apply { writeText("data") }

        assertEquals(IoResult.Done, runBlocking { repo.zipAction(setOf(source.path), dir.path, "out.zip") {} })

        assertTrue(File(dir, "out.zip").isFile)
    }
}
