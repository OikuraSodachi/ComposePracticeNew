package com.todokanai.composepracticenew.tools

/** ~Action -> ViewModel에서 호출하는 method */
import android.content.Context
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.tools.fileaction.CopyAction
import com.todokanai.composepracticenew.tools.fileaction.DeleteAction
import com.todokanai.composepracticenew.tools.fileaction.MoveAction
import com.todokanai.composepracticenew.tools.fileaction.NewFolderAction
import com.todokanai.composepracticenew.tools.fileaction.OpenAction
import com.todokanai.composepracticenew.tools.fileaction.RenameAction
import com.todokanai.composepracticenew.tools.fileaction.UnzipAction
import com.todokanai.composepracticenew.tools.fileaction.ZipAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile

class FileAction(
    val setCurrentPath:(File)->Unit,
    private val currentPathFlow: StateFlow<File>
) {

    private val notiSorter = CompletedNotiSorter()

    /** CoroutineScope 감싸고 invokeOnCompletion? */
    fun openAction(
        context:Context,
        selected: File
    ){
        if (selected.isDirectory) {
            setCurrentPath(selected)
        } else {
            OpenAction().openFile(context, selected)   // 파일이면 열기
        }
    }       // Done

    fun renameAction(
        selectedFile:File,
        name:String
    ){
        actionWrapper(
            action = {
                RenameAction().renameAction(
                    selectedFile = selectedFile,
                    name = name
                )
                     },
            completionCallback = {onComplete(selectedFile.parentFile,null)}
        )
    }       // Done

    fun copyAction(
        files:Array<File>,
        currentPath: File,
        setProgressState:(ProgressState)->Unit

    ){
        actionWrapper(
            action = {
                CopyAction(
                    onSpaceRequired = {onSpaceRequired()}
                ).copyFiles(
                    files = files,
                    target = currentPath,
                  //  progressCallback = { myNoti.copyProgressNoti(it) },
                    progressCallback = { setProgressState(it) },
                )
            },
            completionCallback = { onComplete(currentPath, ACTION_KEY_COPY) }
        )
    }       // Done

    fun moveAction(
        files:Array<File>,
        currentPath: File,
        setProgressState:(ProgressState)->Unit
    ){
        actionWrapper(
            action = {
                MoveAction(
                    onSpaceRequired = {onSpaceRequired()}
                ).moveAction(
                    files = files,
                    path = currentPath,
                    //progressCallback = {myNoti.moveProgressNoti(it)},
                    progressCallback = {setProgressState(it)},
                    )
                     },
            completionCallback = { onComplete(currentPath, ACTION_KEY_MOVE)}
        )
    }           // Done

    fun deleteAction(
        files:Array<File>,
        setProgressState:(ProgressState)->Unit
    ){

        actionWrapper(
            action =  {
                DeleteAction().deleteFiles(
                    files = files,
                    //progressCallback = {myNoti.deleteProgressNoti(it,totalNumber)}
                    progressCallback = {setProgressState(it)}
            )
                      },
            completionCallback = {
                onComplete(files.first().parentFile, ACTION_KEY_DELETE)
            }
        )

    }       // Done


    fun newFolderAction(
        currentPath:File,
        folderName:String
    ){
        actionWrapper(
            action = {
                NewFolderAction().newFolderAction(
                    path = currentPath,
                    folderName = folderName
                )
            },
            completionCallback = {onComplete(currentPath, null)}
        )
    }       // Done


    fun zipAction(
        files:Array<File>,
        zipFileName:String,
        setProgressState:(ProgressState)->Unit
    ){
        val targetPath = currentPathFlow.value.parentFile   // 압축할 목록의 parent 경로 ( == files.first().parentFile )
        val zipFile = File("$targetPath/$zipFileName.zip")

        actionWrapper(
            action = {
                ZipAction(
                    onSpaceRequired = {onSpaceRequired()}
                ).zipFiles(
                    files = files,
                    zipFile = zipFile,
                    progressCallback = {setProgressState(it)}
                )
            },
            completionCallback = { onComplete(targetPath, ACTION_KEY_ZIP) },
        )
    }

    fun unzipAction(
        zipFile:ZipFile,
        currentPath:File,
        unzipHere:Boolean,
        setProgressState:(ProgressState)->Unit
    ){
        actionWrapper(
            action = {
                UnzipAction(
                    onSpaceRequired = {onSpaceRequired()}
                ).unzip(
                    zipFile = zipFile,
                    target = currentPath,
                    progressCallback = {setProgressState(it)},
                    unzipHere = unzipHere
                )
            },
            completionCallback = { onComplete(currentPath.parentFile, ACTION_KEY_UNZIP) },
        )

    }      // Done

    //-----------------------
    // 여기부터 Private

    /** Action 완료후 onComplete 호출 용도
     *
     *  CoroutineScope(Dispatchers.IO) 포함되어 있음
     */
    private fun actionWrapper(action:()->Unit, completionCallback:()->Unit){
        CoroutineScope(Dispatchers.IO).launch {
            action()
        }.invokeOnCompletion {
            completionCallback()
        }
    }

    private fun onSpaceRequired() = LogTool().makeShortToast("Not enough space")


    /** path에 null 입력시 update 미발생
     *
     *  actionKey에 null 입력시 알림은 미발생
     */

    private fun onComplete(
        path:File?,
        actionKey:Int?
    ){
        CoroutineScope(Dispatchers.IO).launch {
            if(currentPathFlow.value == path) {                     // 현재 경로와 업데이트 대상 경로가 같을 경우
                path.listFiles()?.let {         // file.listFiles()?.let -> 접근 가능한 경로일 경우
                    setCurrentPath(path)
                }
            }
            actionKey?.let {
                notiSorter.callback(actionKey)
            }
        }
    }
}