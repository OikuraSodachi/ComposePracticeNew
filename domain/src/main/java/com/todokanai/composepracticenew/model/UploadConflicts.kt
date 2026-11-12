package com.todokanai.composepracticenew.model

/**
 * 업로드 전에 사용자에게 확인할 겹침. 실행할 목적지를 판정에 쓴 목록의 경로로 고정해, 판정한 폴더와 실행하는 폴더가 달라지지 않게 한다.
 * @param destinationPath 판정한 목록의 경로 — 업로드는 이 경로에 실행한다
 * @param conflicts 목적지에 같은 이름이 이미 있는 업로드 대상 항목(원래 순서 유지)
 * @param collisions 함께 올리는 항목끼리 목적지에서 같은 항목이 될 수 있는 이름 묶음(폴더 안쪽 포함)
 */
data class UploadConflicts<T>(val destinationPath: String, val conflicts: List<T>, val collisions: List<NameCollision>)

/**
 * 같은 폴더에 함께 올라가는 이름 중 목적지 저장소가 같은 항목으로 다룰 수 있는 이름 묶음. 목적지가 실제로 두 이름을 합치는지는 알 수 없다.
 * @param folderLabel 이름들이 놓일 폴더의 표시용 경로(올리는 폴더 이름부터). 처음 선택한 항목끼리면 null
 * @param names 같은 항목이 될 수 있는 이름(두 개 이상, 올리는 순서)
 */
data class NameCollision(val folderLabel: String?, val names: List<String>)
