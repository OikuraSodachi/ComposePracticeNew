package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** LocalDataRepository의 DataStore + Room 기반 위임 구현체. */
@Singleton
class LocalDataRepositoryImpl @Inject constructor(
    private val dataStore: DataStoreRepository,
    private val remoteStorage: RemoteStorageRepositoryImpl
) : LocalDataRepository {
    override val sortBy: Flow<String> get() = dataStore.sortBy
    override fun saveSortBy(value: String) = dataStore.saveSortBy(value)
    override fun getAll(): Flow<List<RemoteStorageItem>> = remoteStorage.getAll()
    override suspend fun insert(item: RemoteStorageItem) = remoteStorage.insert(item)
    override suspend fun delete(item: RemoteStorageItem) = remoteStorage.delete(item)
}
