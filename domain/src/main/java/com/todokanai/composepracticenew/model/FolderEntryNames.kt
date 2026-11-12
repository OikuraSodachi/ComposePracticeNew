package com.todokanai.composepracticenew.model

/**
 * 폴더 하나의 바로 안에 있는 항목 이름. 그 폴더를 올리면 같은 폴더에 함께 놓일 이름이다.
 * @param folderLabel 폴더의 표시용 경로(읽기 시작한 폴더 이름부터, '/' 구분). 표시 전용이며 경로로 쓰지 않는다
 * @param names 폴더 바로 안의 항목 이름
 */
data class FolderEntryNames(val folderLabel: String, val names: List<String>)
