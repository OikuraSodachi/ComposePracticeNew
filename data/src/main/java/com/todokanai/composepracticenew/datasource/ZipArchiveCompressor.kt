package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import javax.inject.Inject

/**
 * zip4j로 ZIP 아카이브를 쓰는 ArchiveCompressor. 폴더는 `이름/` 항목으로 담아, 빈 폴더도 압축 해제 시 그대로 복원된다.
 * 비밀번호를 받으면 파일 항목을 AES-256으로 암호화한다. 폴더 항목은 내용이 없어 암호화하지 않는다(ZIP은 이름을 암호화하지 않으므로 파일 이름도 보인다).
 * 이름은 UTF-8로 쓰고 UTF-8 표시를 붙인다.
 */
class ZipArchiveCompressor @Inject constructor() : ArchiveCompressor {

    override fun write(entries: List<ArchiveSource>, output: File, password: String?, tracker: ByteProgressTracker) {
        ZipOutputStream(output.outputStream(), password?.toCharArray()).use { zos ->
            entries.forEach { entry ->
                when (entry) {
                    is ArchiveSource.Folder -> writeFolder(zos, entry)
                    is ArchiveSource.Regular -> writeFile(zos, entry, password != null, tracker)
                }
            }
        }
    }

    /**
     * 폴더 항목을 내용 없는 `이름/` 항목으로 쓴다.
     * @param zos 쓰는 중인 ZIP 출력
     * @param entry 쓸 폴더 항목
     */
    private fun writeFolder(zos: ZipOutputStream, entry: ArchiveSource.Folder) {
        zos.putNextEntry(ZipParameters().apply { fileNameInZip = "${entry.name}/" })
        zos.closeEntry()
    }

    /**
     * 파일 항목의 내용을 쓰며 [tracker]로 진행을 보고한다.
     * @param zos 쓰는 중인 ZIP 출력
     * @param entry 쓸 파일 항목
     * @param encrypt AES-256으로 암호화하는지
     * @param tracker 누적 진행률 보고기
     */
    private fun writeFile(zos: ZipOutputStream, entry: ArchiveSource.Regular, encrypt: Boolean, tracker: ByteProgressTracker) {
        tracker.startFile()
        zos.putNextEntry(fileParameters(entry.name, encrypt))
        entry.file.inputStream().use { input ->
            tracker.pump(input, zos, entry.file.name, entry.bytes)
        }
        zos.closeEntry()
        tracker.onFileCompleted(entry.file.name, entry.bytes)
    }

    /**
     * 파일 항목의 쓰기 설정을 만든다.
     * @param name 아카이브 안의 '/' 구분 경로
     * @param encrypt AES-256으로 암호화하는지
     * @return 항목 이름과 암호화 설정을 담은 ZipParameters
     */
    private fun fileParameters(name: String, encrypt: Boolean): ZipParameters = ZipParameters().apply {
        fileNameInZip = name
        if (encrypt) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }
}
