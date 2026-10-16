package com.todokanai.composepracticenew.model

/**
 * 진행률이 보고되는 파일 작업의 종류와 그 도메인 속성을 정의한다.
 * 레이어별 표시 문자열은 각 레이어가 이 enum에 대한 exhaustive `when`으로 매핑해 새 항목 누락을 컴파일 단계에서 드러낸다.
 * @param id 알림 Intent extra·PendingIntent requestCode로 직렬화되는 고유 정수 식별자
 * @param isRemote 원격 스토리지와 데이터를 주고받는 전송 작업이면 true
 */
enum class FileOperationType(val id: Int, val isRemote: Boolean) {
    COPY(20, false),
    MOVE(21, false),
    DELETE(22, false),
    ZIP(23, false),
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
