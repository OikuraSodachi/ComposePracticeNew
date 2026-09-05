package com.todokanai.composepracticenew.viewmodel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase
) : ViewModel() {

    fun getPermission(activity: Activity) {
        viewModelScope.launch {
            if (!checkPermission(activity)) {
                requestPermission(activity)
            }
            requestStorageManageAccess(activity)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission(activity)
            }
        }
    }

    private fun requestNotificationPermission(activity: Activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                112
            )
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
                activity.getString(R.string.toast_storage_permission),
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
            getStorageListUseCase.execute(getPhysicalStorages(activity))
        } else {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val uri: Uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        }
    }

    private fun getPhysicalStorages(context: Context): List<StorageVolumeInfo> {
        val defaultStorage = Environment.getExternalStorageDirectory()
        val volumes = context.getSystemService(StorageManager::class.java)?.storageVolumes
        val storageList = mutableListOf(
            StorageVolumeInfo(defaultStorage.absolutePath, defaultStorage.totalSpace, defaultStorage.freeSpace)
        )
        volumes?.forEach { volume ->
            if (!volume.isPrimary && volume.isRemovable) {
                val sdCard = volume.directory
                if (sdCard != null) {
                    storageList.add(StorageVolumeInfo(sdCard.absolutePath, sdCard.totalSpace, sdCard.freeSpace))
                }
            }
        }
        return storageList
    }

}
