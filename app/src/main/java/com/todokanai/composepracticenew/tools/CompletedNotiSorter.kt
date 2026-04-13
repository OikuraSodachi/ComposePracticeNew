package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.myobjects.Constants

class CompletedNotiSorter {
    private val myNoti = MyNotification()

    fun callback(actionKey: Int) = myNoti.completedNotification(
        title = "",
        message = sorter(actionKey)
    )

    private fun sorter(actionKey:Int):String{
        when(actionKey){
            Constants.ACTION_KEY_DELETE -> {
                return deleteComplete()
            }
            Constants.ACTION_KEY_UNZIP ->{
                return  unzipComplete()
            }
            Constants.ACTION_KEY_COPY ->{
                return copyComplete()
            }
            Constants.ACTION_KEY_MOVE ->{
                return moveComplete()
            }
            Constants.ACTION_KEY_ZIP ->{
                return zipComplete()
            }
            else -> {
                return "Invalid Action Key!!"
            }
        }
    }






    /*
    /** Delete 완료 message */
    private fun deleteComplete(selectedFileNumber: Int):String{
        return "Deleted $selectedFileNumber files and its subdirectories"
    }

    private fun copyComplete(selectedFileNumber:Int):String{
        return  "Copied $selectedFileNumber files and its subdirectories"
    }

    private fun moveComplete(selectedFileNumber:Int):String{
        return  "Moved $selectedFileNumber files and its subdirectories"
    }

    private fun zipComplete(selectedFileNumber:Int):String{
        return  "Zipped $selectedFileNumber files and its subdirectories"
    }
    private fun unzipComplete(selectedFileNumber:Int):String{
        return  "Unzipped $selectedFileNumber files and its subdirectories"
    }


     */



    /** Delete 완료 message */
    private fun deleteComplete():String{
        return "Deleted"
    }

    private fun copyComplete():String{
        return  "Copied"
    }

    private fun moveComplete():String{
        return  "Moved"
    }

    private fun zipComplete():String{
        return  "Zipped"
    }
    private fun unzipComplete():String{
        return  "Unzipped"
    }


}