package com.todokanai.composepracticenew.datasource

import java.io.File
import java.io.IOException

/**
 * 파일 이름이 압축 해제에서 맡는 역할. 이름만으로 정하며 파일을 읽지 않는다.
 * 분할 압축은 시작 볼륨으로만 풀고, 다른 볼륨은 같은 폴더·같은 세트의 시작 볼륨과 함께 받았을 때 그 시작 볼륨에 합친다.
 */
sealed interface ArchiveNameRole {

    /** 이 파일이 속한 볼륨 세트. */
    val set: VolumeSet

    /**
     * 압축 해제를 시작하는 파일: 단일 아카이브 또는 분할 압축의 시작 볼륨.
     * @param set 이 파일이 속한 볼륨 세트
     */
    data class Entry(override val set: VolumeSet) : ArchiveNameRole

    /**
     * 분할 압축의 시작 볼륨이 아닌 볼륨. 혼자서는 풀지 않는다.
     * @param set 이 파일이 속한 볼륨 세트
     */
    data class LaterVolume(override val set: VolumeSet) : ArchiveNameRole
}

/**
 * 한 아카이브를 이루는 볼륨들의 세트.
 * @param id 같은 폴더 안에서 세트를 가리는 값. 같은 이름의 다른 분할 방식(`x.zip`+`x.z01`과 `x.zip.001`)은 다른 값이다
 * @param folderName 아카이브 폴더에 풀 때 쓰는 폴더 이름(볼륨 번호와 확장자를 뺀 이름)
 * @param layout 볼륨을 읽는 방식
 */
data class VolumeSet(val id: String, val folderName: String, val layout: VolumeLayout)

/** 분할 압축의 볼륨을 읽는 방식. */
enum class VolumeLayout {
    /** 시작 파일 하나를 연다. 다른 볼륨이 있으면 읽는 라이브러리가 그 이름을 계산해 찾는다(단일 아카이브 포함). */
    SELF_LOCATING,

    /** 아카이브 하나를 바이트 단위로 자른 번호 볼륨(`.001`, `.002` …)이다. 이어 붙이면 원래 아카이브가 된다. */
    NUMBERED,
}

/**
 * 분할 압축의 볼륨이 없어 풀지 못했음을 알리는 입출력 실패.
 * @param archiveName 압축 해제를 시작한 볼륨의 파일 이름
 * @param volumeName 없는 볼륨의 파일 이름
 */
class MissingArchiveVolumeException(val archiveName: String, val volumeName: String) :
    IOException("분할 압축 $archiveName 의 볼륨이 없습니다: $volumeName")

/** RAR5·RAR3의 `이름.partN.rar` 볼륨 이름. */
private val RAR_PART = Regex("""(.*)\.part(\d+)\.rar""", RegexOption.IGNORE_CASE)

/** 옛 방식 RAR의 시작 볼륨이 아닌 볼륨 이름(`이름.r00`, `이름.r01` …). */
private val RAR_OLD_VOLUME = Regex("""(.*)\.r(\d{2})""", RegexOption.IGNORE_CASE)

/** PKWARE 분할 ZIP의 시작 볼륨이 아닌 볼륨 이름(`이름.z01`, `이름.z02` …, 100번째부터 `이름.z100`). */
private val ZIP_SPLIT_VOLUME = Regex("""(.*)\.z(\d{2,})""", RegexOption.IGNORE_CASE)

/**
 * RAR 파일 이름의 역할을 정한다. `이름.part1.rar`(`part01` 등 0을 앞에 붙인 번호 포함)과 옛 방식의 `이름.rar`이 시작 볼륨이고,
 * `이름.partN.rar`(N ≥ 2)과 `이름.r00`·`이름.r01` …이 다른 볼륨이다. 단일 아카이브도 `이름.rar`이나 `이름.part1.rar`로 시작 볼륨과 같게 다룬다.
 * 이름이 `*.part2.rar`인 단일 아카이브, `.r99` 다음의 `.s00` 볼륨은 지원 범위 밖이다.
 * @param fileName 파일 이름
 * @return 역할. RAR 이름이 아니면 null
 */
internal fun rarNameRoleOf(fileName: String): ArchiveNameRole? {
    RAR_PART.matchEntire(fileName)?.let { match ->
        val (base, number) = match.destructured
        return roleOf(VolumeSet("$base.part.rar", folderNameOf(base, fileName), VolumeLayout.SELF_LOCATING), number.toInt() == 1)
    }
    RAR_OLD_VOLUME.matchEntire(fileName)?.let { match ->
        val base = match.groupValues[1]
        return ArchiveNameRole.LaterVolume(VolumeSet("$base.rar", folderNameOf(base, fileName), VolumeLayout.SELF_LOCATING))
    }
    return singleFileRoleOf(fileName, ".rar")
}

/**
 * ZIP 파일 이름의 역할을 정한다. `이름.zip`이 시작 파일이고(PKWARE 분할이면 내용 목록이 이 마지막 볼륨에 있다) `이름.z01` …이 다른 볼륨이다.
 * 번호 분할은 `이름.zip.001`이 시작 볼륨이고 `.002` …이 다른 볼륨이다. 번호 분할의 확장자는 zip4j가 알아보는 소문자 `.zip.###`만 받는다.
 * @param fileName 파일 이름
 * @return 역할. ZIP 이름이 아니면 null
 */
internal fun zipNameRoleOf(fileName: String): ArchiveNameRole? {
    numberedRoleOf(fileName, ".zip", ignoreCase = false)?.let { return it }
    ZIP_SPLIT_VOLUME.matchEntire(fileName)?.let { match ->
        val base = match.groupValues[1]
        return ArchiveNameRole.LaterVolume(VolumeSet("$base.zip", folderNameOf(base, fileName), VolumeLayout.SELF_LOCATING))
    }
    return singleFileRoleOf(fileName, ".zip")
}

/**
 * 7z 파일 이름의 역할을 정한다. `이름.7z`는 단일 아카이브, 번호 분할은 `이름.7z.001`이 시작 볼륨이고 `.002` …이 다른 볼륨이다.
 * @param fileName 파일 이름
 * @return 역할. 7z 이름이 아니면 null
 */
internal fun sevenZipNameRoleOf(fileName: String): ArchiveNameRole? =
    numberedRoleOf(fileName, ".7z", ignoreCase = true) ?: singleFileRoleOf(fileName, ".7z")

/**
 * 분할하지 않는 형식의 이름 역할을 정한다.
 * @param fileName 파일 이름
 * @param extension 형식의 확장자(점 포함, 대소문자 구분 없음)
 * @return 이름이 [extension]으로 끝나면 시작 파일 역할, 아니면 null
 */
internal fun singleFileRoleOf(fileName: String, extension: String): ArchiveNameRole? {
    if (!fileName.endsWith(extension, ignoreCase = true)) return null
    val base = fileName.dropLast(extension.length)
    return ArchiveNameRole.Entry(VolumeSet("$base${extension.lowercase()}", folderNameOf(base, fileName), VolumeLayout.SELF_LOCATING))
}

/**
 * `이름[extension].###` 번호 볼륨 이름의 역할을 정한다. `.001`이 시작 볼륨이다.
 * @param fileName 파일 이름
 * @param extension 자르기 전 아카이브의 확장자(점 포함)
 * @param ignoreCase 확장자의 대소문자를 구분하지 않을지
 * @return 역할. 번호 볼륨 이름이 아니면 null
 */
private fun numberedRoleOf(fileName: String, extension: String, ignoreCase: Boolean): ArchiveNameRole? {
    val number = numberedVolumeNumberOf(fileName) ?: return null
    val archiveName = fileName.dropLast(NUMBER_SUFFIX_LENGTH)
    if (!archiveName.endsWith(extension, ignoreCase)) return null
    val base = archiveName.dropLast(extension.length)
    return roleOf(VolumeSet("$base${extension.lowercase()}.###", folderNameOf(base, fileName), VolumeLayout.NUMBERED), number == 1)
}

/**
 * 번호 볼륨 이름의 번호를 읽는다.
 * @param fileName 파일 이름
 * @return `.` 뒤 세 자리 숫자의 번호. 그런 이름이 아니면 null
 */
private fun numberedVolumeNumberOf(fileName: String): Int? {
    if (fileName.length <= NUMBER_SUFFIX_LENGTH || fileName[fileName.length - NUMBER_SUFFIX_LENGTH] != '.') return null
    val digits = fileName.takeLast(NUMBER_SUFFIX_LENGTH - 1)
    return if (digits.all { it in '0'..'9' }) digits.toInt() else null
}

/**
 * 시작 볼륨 여부로 역할을 만든다.
 * @param set 볼륨 세트
 * @param isEntry 시작 볼륨이면 true
 * @return 역할
 */
private fun roleOf(set: VolumeSet, isEntry: Boolean): ArchiveNameRole =
    if (isEntry) ArchiveNameRole.Entry(set) else ArchiveNameRole.LaterVolume(set)

/**
 * 아카이브 폴더 이름을 정한다. 볼륨 번호와 확장자를 뺀 이름이 비면(숨김 표시 점으로 시작하는 `.zip` 등) 파일 이름을 그대로 써서,
 * 이름이 비어 대상 디렉터리에 바로 풀리는 일이 없게 한다.
 * @param base 볼륨 번호와 확장자를 뺀 이름
 * @param fileName 파일 이름
 * @return 폴더 이름
 */
private fun folderNameOf(base: String, fileName: String): String = base.ifEmpty { fileName }

/**
 * 번호 볼륨 세트의 볼륨 파일을 차례로 찾는다. `.001` 다음 번호가 없는 곳을 세트의 끝으로 보며, 그 뒤 번호의 볼륨이 폴더에 있으면 가운데가 빠진 것으로 알린다.
 * 마지막 볼륨이 빠진 세트는 이름으로 알아낼 수 없어 읽는 쪽이 손상된 아카이브로 알린다(지원 범위 밖).
 * @param first 시작 볼륨(`.001`) 파일
 * @return 번호 순서의 볼륨 파일들([first] 포함)
 * @throws MissingArchiveVolumeException 가운데 볼륨이 없을 때
 */
internal fun numberedVolumesOf(first: File): List<File> {
    val prefix = first.name.dropLast(NUMBER_SUFFIX_LENGTH - 1)
    val volumes = generateSequence(1) { it + 1 }
        .map { File(first.parentFile, prefix + it.toString().padStart(NUMBER_SUFFIX_LENGTH - 1, '0')) }
        .takeWhile { it.isFile }
        .toList()
    val lastNumber = volumes.size
    val later = first.parentFile?.list()?.mapNotNull { name ->
        if (name.startsWith(prefix) && name.length == first.name.length) numberedVolumeNumberOf(name) else null
    }.orEmpty().filter { it > lastNumber }
    if (later.isNotEmpty()) {
        val missing = prefix + (lastNumber + 1).toString().padStart(NUMBER_SUFFIX_LENGTH - 1, '0')
        throw MissingArchiveVolumeException(first.name, missing)
    }
    return volumes
}

/** 번호 볼륨 이름 끝의 `.###` 길이. */
private const val NUMBER_SUFFIX_LENGTH = 4
