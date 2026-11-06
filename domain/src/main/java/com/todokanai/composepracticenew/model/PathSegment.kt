package com.todokanai.composepracticenew.model

/**
 * 경로 표시줄에 보여줄 경로 한 단계. 루트부터 현재 디렉터리까지 이어지는 조상 경로 하나를 나타내며, 경로 이동에 쓸 값만 갖는다.
 * @param name 표시 이름(저장소가 정한 디렉터리 이름 — FileNavigatorRepository.displayNameOf)
 * @param path 이 단계의 디렉터리 경로
 */
data class PathSegment(val name: String, val path: String)
