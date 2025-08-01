package uz.shukrullaev.com.sample

import org.springframework.data.domain.Page


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 12/07/2025 3:39 pm
 */

fun Boolean.runIfTrue(func: () -> Unit) {
    if (this) func()
}

fun Boolean.runIfFalse(func: () -> Unit) {
    if (!this) func()
}

fun Page<User>.toDTODocPermission(targetId: Long): Page<UserDocumentationPermissionDTO> =
    this.map { it.toDTODocPermission(targetId) }

fun Page<User>.toDTOSamplePermission(targetId: Long): Page<UserSamplePermissionDto> =
    this.map { it.toDTOSamplePermission(targetId) }
