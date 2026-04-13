package com.todokanai.composepracticenew.viewmodel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.dataclass.StorageHolderItem
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.variables.Variables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel(){

    companion object{
        private val _physicalStorageList = MutableStateFlow<List<StorageHolderItem>>(emptyList())       // 물리적 저장소 목록
        val physicalStorageList : StateFlow<List<StorageHolderItem>>
            get() = _physicalStorageList
    }

    private val selectMode = Variables.selectMode
    private val vars = Variables()
    private val currentPath = Variables.currentPath

    fun getPermission(activity: Activity){
        viewModelScope.launch {
            if (!checkPermission(activity)) {
                requestPermission(activity)
            }
            requestStorageManageAccess(activity)
        }
    }

    private fun requestPermission(activity: Activity) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            Toast.makeText(
                activity,
                "Storage permission is requires,please allow from settings",
                Toast.LENGTH_SHORT
            ).show()
        } else ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            111
        )
    }

    private fun checkPermission(activity: Activity): Boolean {
        val result = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStorageManageAccess(activity: Activity) {
        if (Environment.isExternalStorageManager()) {
            val storages = getPhysicalStorages(activity)
            val storageHolderList = DataConverter(activity).storageHolderItemList(storages)
            _physicalStorageList.value = storageHolderList
        } else {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val uri: Uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        }
    }
    private fun getPhysicalStorages(context: Context):List<File>{
        val defaultStorage = Environment.getExternalStorageDirectory()
        val volumes = context.getSystemService(StorageManager::class.java)?.storageVolumes
        val storageList = mutableListOf<File>(defaultStorage)
        volumes?.forEach { volume ->
            if (!volume.isPrimary && volume.isRemovable) {
                val sdCard = volume.directory
                if (sdCard != null) {
                    storageList.add(sdCard)
                }
            }
        }
        return storageList
    }

    /** viewModelScope 적절하게 배치한건지 잘 몰?루 */
    fun onBackPressed(toStorageFrag:()->Unit){
        viewModelScope.launch {
            if (selectMode.value == Constants.MULTI_SELECT_MODE) {
                vars.setSelectMode(Constants.DEFAULT_MODE)
            } else {
                val parentFile = currentPath.value.parentFile
                if (parentFile?.listFiles() == null) {
                    toStorageFrag()
                } else {
                    vars.setCurrentPath(parentFile)
                }
            }
        }
    }
}