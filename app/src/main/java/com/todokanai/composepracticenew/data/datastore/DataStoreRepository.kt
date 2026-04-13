package com.todokanai.composepracticenew.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.todokanai.composepracticenew.application.MyApplication
import com.todokanai.composepracticenew.myobjects.Constants.BY_DEFAULT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DataStoreRepository {
    companion object{
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mydatastore")
        val DATASTORE_SORT_BY = stringPreferencesKey("datastore_sort_by")
        val DATASTORE_COPY_OVERWRITE = booleanPreferencesKey("datastore_copy_overwrite")
    }
    private val myContext = MyApplication.appContext

    fun saveSortBy(value:String){
        CoroutineScope(Dispatchers.IO).launch {
            myContext.dataStore.edit{
                it[DATASTORE_SORT_BY] = value
            }
        }
    }

    suspend fun sortBy() : String {
        return myContext.dataStore.data.first()[DATASTORE_SORT_BY] ?: BY_DEFAULT
    }

    val sortBy: Flow<String> = myContext.dataStore.data.map{
        it[DATASTORE_SORT_BY]?: BY_DEFAULT
    }


    /** 이렇게 쓰는게 안전한지 아직 몰?루 */
    val sortBy_unstable : StateFlow<String> = myContext.dataStore.data.map{
        it[DATASTORE_SORT_BY]?: BY_DEFAULT
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = SharingStarted.WhileSubscribed(0),
        initialValue = BY_DEFAULT
    )


    fun saveCopyOverwrite(value:Boolean){
        CoroutineScope(Dispatchers.IO).launch {
            myContext.dataStore.edit{
                it[DATASTORE_COPY_OVERWRITE] = value
            }
        }
    }

    suspend fun copyOverwrite(): Boolean {
        return myContext.dataStore.data.first()[DATASTORE_COPY_OVERWRITE] ?: false
    }
}