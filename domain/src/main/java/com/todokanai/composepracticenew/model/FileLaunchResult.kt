package com.todokanai.composepracticenew.model

/** 파일을 다른 앱으로 열려고 한 결과. 열지 못한 경우는 이유별로 나눠, 받는 쪽이 실패를 알리게 한다. */
sealed interface FileLaunchResult {
    /** 파일을 여는 앱에 넘겼다. */
    data object Launched : FileLaunchResult

    /** 파일 형식을 알 수 없어 열지 않았다. 열 수 없는 앱에 넘기지 않기 위해서다. */
    data object UnknownType : FileLaunchResult

    /** 이 형식을 여는 앱이 없다. */
    data object NoHandler : FileLaunchResult

    /** 이 형식을 여는 앱이 있지만 이 앱이 여는 것을 허용하지 않았다(권한 요구·비공개 등). */
    data object Refused : FileLaunchResult
}
