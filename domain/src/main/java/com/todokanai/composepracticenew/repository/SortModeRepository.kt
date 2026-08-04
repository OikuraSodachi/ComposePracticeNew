package com.todokanai.composepracticenew.repository

import kotlinx.coroutines.flow.Flow

/** Contract for persisting and retrieving the user's file sort preference. */
interface SortModeRepository {
    val sortBy: Flow<String>
    fun saveSortBy(value: String)
}
