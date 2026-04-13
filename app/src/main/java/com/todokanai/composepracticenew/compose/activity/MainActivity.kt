package com.todokanai.composepracticenew.compose.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.asLiveData
import com.todokanai.composepracticenew.compose.frag.DirectoryFrag
import com.todokanai.composepracticenew.compose.frag.FileListFrag
import com.todokanai.composepracticenew.compose.frag.OptionFrag
import com.todokanai.composepracticenew.compose.frag.StorageFrag
import com.todokanai.composepracticenew.ui.theme.ComposePracticeTheme
import com.todokanai.composepracticenew.viewmodel.BottomButtonsViewModel
import com.todokanai.composepracticenew.viewmodel.DirectoryViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import com.todokanai.composepracticenew.viewmodel.OptionViewModel
import com.todokanai.composepracticenew.viewmodel.StorageViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var activityResult: ActivityResultLauncher<String>
    private val mViewModel: MainViewModel by viewModels()
    private val fViewModel: FileListViewModel by viewModels()
    private val sViewModel : StorageViewModel by viewModels()
    private val oViewModel : OptionViewModel by viewModels()
    private val dViewModel : DirectoryViewModel by viewModels()
    private val bViewModel : BottomButtonsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(
                activity = this,
                viewModel = fViewModel,
                mViewModel = mViewModel,
                sViewModel = sViewModel,
                oViewModel = oViewModel,
                dViewModel = dViewModel,
                bViewModel = bViewModel
            )
        }

        mViewModel.getPermission(this)

        activityResult =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted)
                    finish()
            }
    }
}

@Composable
private fun HomeScreen(
    activity: MainActivity,
    viewModel:FileListViewModel,
    mViewModel:MainViewModel,
    sViewModel:StorageViewModel,
    oViewModel:OptionViewModel,
    dViewModel: DirectoryViewModel,
    bViewModel:BottomButtonsViewModel
){
    ComposePracticeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var isStorageFrag by remember{mutableStateOf(true)}
            val onBack = activity.onBackPressedDispatcher


            if(isStorageFrag){
                StorageFrag(
                    modifier =Modifier,
                    activity = activity,
                    viewModel = sViewModel,
                    exitStorageFrag ={isStorageFrag = false},
                    setInitialPath = {viewModel.updateCurrentPath(it)}
                )
                onBack.addCallback() {

                }

            } else {
                val progressFlow = remember{viewModel.progressState}

                Column(
                    modifier = Modifier
                ) {
                    OptionFrag(
                        modifier = Modifier,
                        activity = activity,
                        viewModel = oViewModel
                    )
                    DirectoryFrag(
                        modifier = Modifier,
                        viewModel = dViewModel
                    )
                    FileListFrag(
                        modifier = Modifier,
                        viewModel = viewModel,
                        bViewModel = bViewModel
                    )
                }

                /** progress Notification 용도 **/
                SideEffect {
                    progressFlow.asLiveData().observeForever(){ progressState ->
                        progressState.actionKey?.let{ actionKey ->
                            viewModel.progressNoti(actionKey,progressState)
                        }
                    }

                }


                //----------------------------
                onBack.addCallback() {
                    mViewModel.onBackPressed({isStorageFrag = true})
                }

            }
        }
    }
}