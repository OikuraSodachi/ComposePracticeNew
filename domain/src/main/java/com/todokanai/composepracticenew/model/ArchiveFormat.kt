package com.todokanai.composepracticenew.model

/**
 * 압축으로 만드는 아카이브의 형식. 압축 파일 이름의 확장자는 이 값이 정한다(이름 검증·이름 제안·저장소의 파일 생성이 같은 확장자를 쓰도록).
 * 형식을 추가하면 저장소 구현에 그 형식의 압축 구현도 함께 넣는다 — 구현이 없는 형식은 저장소의 지원 형식(`FileActionRepository.supportedArchiveFormats`)에 들지 않아, 그 형식의 압축은 실행 전에 거부된다.
 * @param extension 이 형식의 압축 파일 이름 뒤에 붙는 확장자('.' 포함)
 */
enum class ArchiveFormat(val extension: String) {
    /** ZIP 아카이브. */
    ZIP(".zip")
}
