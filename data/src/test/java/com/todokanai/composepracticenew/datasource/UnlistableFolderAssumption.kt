package com.todokanai.composepracticenew.datasource

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

/**
 * [folder]의 안을 나열할 수 없게 한다. POSIX 권한으로 막지 못하면(Windows) 소유자의 폴더 나열을 거부하는 ACL 항목을 더한다.
 * 어느 쪽으로도 막을 수 없는 환경(root 계정 등)이면 테스트를 건너뛴다. 임시 폴더를 지울 수 있도록 테스트가 끝나면 [restoreListable]을 불러야 한다.
 * @param folder 나열할 수 없게 할 폴더
 */
internal fun assumeUnlistable(folder: File) {
    if (!(folder.setReadable(false) && folder.listFiles() == null)) denyListingByAcl(folder)
    assumeTrue(folder.listFiles() == null)
}

/**
 * [assumeUnlistable]이 막은 [folder]의 나열을 다시 허용한다. 테스트가 옮겨 이미 없는 폴더면 아무것도 하지 않는다.
 * @param folder 되돌릴 폴더
 */
internal fun restoreListable(folder: File) {
    if (!folder.exists()) return
    folder.setReadable(true)
    val view = aclViewOf(folder) ?: return
    view.acl = view.acl.filterNot(::isListingDenial)
}

/** [folder]에 소유자의 폴더 나열을 거부하는 ACL 항목을 맨 앞에 더한다(거부 항목이 허용보다 먼저 적용되도록). ACL이 없는 파일 시스템이면 아무것도 하지 않는다. */
private fun denyListingByAcl(folder: File) {
    val view = aclViewOf(folder) ?: return
    val denial = AclEntry.newBuilder()
        .setType(AclEntryType.DENY)
        .setPrincipal(view.owner)
        .setPermissions(AclEntryPermission.LIST_DIRECTORY)
        .build()
    view.acl = listOf(denial) + view.acl
}

/** [folder]의 ACL 보기. ACL이 없는 파일 시스템이면 null. */
private fun aclViewOf(folder: File): AclFileAttributeView? =
    Files.getFileAttributeView(folder.toPath(), AclFileAttributeView::class.java)

/** [entry]가 [denyListingByAcl]이 더한 폴더 나열 거부 항목인지. */
private fun isListingDenial(entry: AclEntry): Boolean =
    entry.type() == AclEntryType.DENY && AclEntryPermission.LIST_DIRECTORY in entry.permissions()
