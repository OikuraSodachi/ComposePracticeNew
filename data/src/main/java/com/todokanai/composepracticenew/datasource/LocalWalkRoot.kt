package com.todokanai.composepracticenew.datasource

import java.nio.file.Files
import java.nio.file.Path

/**
 * walkFileTree로 훑을 루트를 정한다. walkFileTree는 링크를 따라가지 않으면 루트가 링크일 때 그 안을 훑지 않으므로, 루트 자신이 링크면 가리키는 경로로 푼다.
 * @param root 훑을 대상 경로
 * @return 루트가 심볼릭 링크면 링크가 가리키는 경로, 아니면 [root] 그대로. 링크를 풀지 못하면(끊긴 링크 등) IOException을 던진다
 */
internal fun walkRootOf(root: Path): Path = if (Files.isSymbolicLink(root)) root.toRealPath() else root
