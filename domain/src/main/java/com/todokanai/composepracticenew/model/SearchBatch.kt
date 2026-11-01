package com.todokanai.composepracticenew.model

/**
 * 검색이 지난 보고 이후 새로 찾은 항목과 읽지 못한 항목의 묶음. 검색은 이 묶음을 찾는 대로 여러 번 내보낸다.
 * @param found 이름이 검색어와 맞는 항목. 찾은 순서다
 * @param unreadable 읽지 못한 항목(나열하지 못한 폴더 등). 이 아래는 훑지 못해 결과가 빠졌을 수 있다
 */
data class SearchBatch(val found: List<FileHolderItem>, val unreadable: List<FailedItem>)
