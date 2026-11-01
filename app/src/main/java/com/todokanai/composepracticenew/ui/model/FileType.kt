package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.FileNameParts

/** 파일 목록에서 아이콘(placeholder)과 미리보기 방식을 고르는 기준이 되는 항목 종류. 이름의 확장자로만 정하므로 로컬·원격 목록에 같은 규칙이 적용된다. */
enum class FileType {
    Folder, Image, Video, Audio, Document, Pdf, Archive, Apk, Other;

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "m4v", "flv")
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "amr", "mid", "midi")
        private val DOCUMENT_EXTENSIONS = setOf(
            "txt", "md", "log", "csv", "json", "xml",
            "doc", "docx", "hwp", "hwpx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp"
        )
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

        /**
         * 이름과 폴더 여부로 항목 종류를 정한다. 확장자는 [FileNameParts.extensionOf] 규칙으로 꺼내 대소문자를 구분하지 않는다.
         * @param name 파일 또는 폴더 이름
         * @param isDirectory 폴더 여부
         * @return 폴더면 [Folder], 알려진 확장자면 해당 종류, 확장자가 없거나 모르는 확장자면 [Other]
         */
        fun of(name: String, isDirectory: Boolean): FileType {
            if (isDirectory) return Folder
            val extension = FileNameParts.extensionOf(name)?.lowercase() ?: return Other
            return when (extension) {
                in IMAGE_EXTENSIONS -> Image
                in VIDEO_EXTENSIONS -> Video
                in AUDIO_EXTENSIONS -> Audio
                in DOCUMENT_EXTENSIONS -> Document
                "pdf" -> Pdf
                in ARCHIVE_EXTENSIONS -> Archive
                "apk" -> Apk
                else -> Other
            }
        }
    }
}
