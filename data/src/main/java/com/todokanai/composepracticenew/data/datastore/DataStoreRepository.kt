package com.todokanai.composepracticenew.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.todokanai.composepracticenew.myobjects.Constants.BY_DEFAULT
import com.todokanai.composepracticenew.repository.SortModeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreRepository @Inject constructor(@ApplicationContext private val context: Context) : SortModeRepository {
    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mydatastore")
        val DATASTORE_SORT_BY = stringPreferencesKey("datastore_sort_by")
        val DATASTORE_COPY_OVERWRITE = booleanPreferencesKey("datastore_copy_overwrite")
    }

    override fun saveSortBy(value: String) {
        CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.edit {
                it[DATASTORE_SORT_BY] = value
            }
        }
    }

    suspend fun sortBy(): String {
        return context.dataStore.data.first()[DATASTORE_SORT_BY] ?: BY_DEFAULT
    }

    override val sortBy: Flow<String> = context.dataStore.data.map {
        it[DATASTORE_SORT_BY] ?: BY_DEFAULT
    }

    val sortBy_unstable: StateFlow<String> = context.dataStore.data.map {
        it[DATASTORE_SORT_BY] ?: BY_DEFAULT
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = SharingStarted.WhileSubscribed(0),
        initialValue = BY_DEFAULT
    )

    fun saveCopyOverwrite(value: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.edit {
                it[DATASTORE_COPY_OVERWRITE] = value
            }
        }
    }

    suspend fun copyOverwrite(): Boolean {
        return context.dataStore.data.first()[DATASTORE_COPY_OVERWRITE] ?: false
    }
}
