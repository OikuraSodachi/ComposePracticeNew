package com.todokanai.composepracticenew.model

/**
 * 압축 해제한 항목을 목적지 폴더의 어디에 둘지 정하는 배치 방식.
 * 해제 위치와 목적지에서 겹칠 수 있는 이름(충돌 판정)이 함께 이 값을 따르므로, 둘이 어긋나지 않는다.
 */
sealed interface ExtractLayout {

    /** 목적지에 아카이브 이름(확장자 제외)의 폴더를 만들고 그 안에 푼다. 목적지에서 겹칠 수 있는 이름은 그 폴더 하나다. */
    data object IntoArchiveFolder : ExtractLayout

    /** 목적지에 바로 푼다. 목적지에서 겹칠 수 있는 이름은 아카이브의 최상위 항목들이다. */
    data object IntoDestination : ExtractLayout
}
