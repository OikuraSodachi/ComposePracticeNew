package com.todokanai.composepracticenew.tools.independent

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** readableFileSize_td가 1024 단위 자릿수 그룹에 대응해 붙이는 단위 접미사. */
private val FILE_SIZE_UNITS =arrayOf("B", "kB", "MB", "GB", "TB")

/** 스레드별 DecimalFormat 캐시 — DecimalFormat은 스레드 안전하지 않고 생성 비용이 커서 호출마다 만들지 않는다. 기본 Locale이 바뀌면 다시 만든다. */
private val fileSizeFormat = ThreadLocal<Pair<Locale, DecimalFormat>>()

private fun currentFileSizeFormat(): DecimalFormat {
    val locale = Locale.getDefault()
    fileSizeFormat.get()?.takeIf { it.first == locale }?.let { return it.second }
    return DecimalFormat("#,##0.#").also { fileSizeFormat.set(locale to it) }
}

/** Todokanai */
fun readableFileSize_td(size: Long): String {
    if (size <= 0) return "0"
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return currentFileSizeFormat().format(
        size / 1024.0.pow(digitGroups.toDouble())
    ) + " " + FILE_SIZE_UNITS[digitGroups]
}
