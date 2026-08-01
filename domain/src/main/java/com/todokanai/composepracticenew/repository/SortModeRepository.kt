package com.todokanai.composepracticenew.repository

/** Contract for persisting and retrieving the user's file sort preference. */
interface SortModeRepository {
    fun saveSortBy(value: String)
}
