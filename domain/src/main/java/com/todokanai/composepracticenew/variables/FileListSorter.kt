package com.todokanai.composepracticenew.variables

import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DEFAULT
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_DESCENDING

class FileListSorter {

    companion object {
        val sortModeListOriginal = listOf(
            BY_DEFAULT,
            BY_NAME_ASCENDING,
            BY_NAME_DESCENDING,
            BY_SIZE_ASCENDING,
            BY_SIZE_DESCENDING,
            BY_TYPE_ASCENDING,
            BY_TYPE_DESCENDING,
            BY_DATE_ASCENDING,
            BY_DATE_DESCENDING
        )
    }

    fun getSortModeCallbackList(callback: (text: String) -> Unit): List<Pair<String, () -> Unit>> {
        val result = mutableListOf<Pair<String, () -> Unit>>()
        sortModeListOriginal.forEach { text ->
            result.add(Pair(text, { callback(text) }))
        }
        return result
    }
}