package com.todokanai.composepracticenew.repository

import java.text.Normalizer

/**
 * 대소문자와 Unicode 정규형만 다른 이름끼리 같은 값을 돌려준다. PathAwareFileSystem 구현들이 이름 비교 규칙에 함께 쓴다.
 * 정규화(NFD)는 조합형·분해형을 같은 이름으로 보는 볼륨(ext4 casefold)을, 대문자 변환 뒤 소문자 변환은 한 문자가 여러 문자로 바뀌는 매핑("ß" → "SS")까지 포함해
 * 대소문자를 무시하는 볼륨(ext4 casefold, FAT·exFAT의 대문자 변환표)을 함께 덮는다. 대소문자 변환이 정규형을 깰 수 있어 변환 뒤에 다시 정규화한다.
 * @param name 비교할 항목 이름
 * @return 비교 값
 */
internal fun caseAndNormalizationInsensitiveKey(name: String): String =
    Normalizer.normalize(Normalizer.normalize(name, Normalizer.Form.NFD).uppercase().lowercase(), Normalizer.Form.NFD)
