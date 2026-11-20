package com.todokanai.composepracticenew.model

/**
 * 진행률이 보고되는 파일 작업의 종류와 그 도메인 속성을 정의한다.
 * 레이어별 표시 문자열은 각 레이어가 이 enum에 대한 exhaustive `when`으로 매핑해 새 항목 누락을 컴파일 단계에서 드러낸다.
 * @param id 알림 Intent extra·PendingIntent requestCode로 직렬화되는 고유 정수 식별자
 * @param isRemote 원격 스토리지를 다루는 작업(전송·원격 삭제)이면 true — 진행 알림을 누르면 원격 화면으로 간다
 */
enum class FileOperationType(val id: Int, val isRemote: Boolean) {
    COPY(20, false),
    MOVE(21, false),
    DELETE(22, false),
    /** 원격 스토리지의 항목 삭제(#334). 로컬 삭제와 문구는 같고, 진행 알림이 원격 화면을 가리키도록 따로 둔다(#466). */
    REMOTE_DELETE(27, true),
    COMPRESS(23, false),
    UNZIP(24, false),
    DOWNLOAD(25, true),
    UPLOAD(26, true);

    companion object {
        /**
         * 직렬화된 [id]로부터 작업 종류를 복원한다.
         * @param id [FileOperationType.id] 값
         * @return 일치하는 작업 종류, 없으면 null
         */
        fun fromId(id: Int): FileOperationType? = values().firstOrNull { it.id == id }
    }
}
