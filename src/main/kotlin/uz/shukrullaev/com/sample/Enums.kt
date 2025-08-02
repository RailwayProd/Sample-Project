package uz.shukrullaev.com.sample


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:15 pm
 */

enum class Role {
    ADMIN, OPERATOR, DIRECTOR;

    companion object {
        fun fromAuthority(authority: String): Role? {
            return values().firstOrNull { "ROLE_${it.name}" == authority }
        }
    }
}

enum class OrderDirection {
    ASC, DESC
}

enum class Status {
    ACTIVE,
    INACTIVE
}

enum class FileDownloadStatus {
    DOWNLOADED,
    PROGRESS,
    CREATED,
    ERROR,
    DOWNLOADING
}

enum class Permissions {
    READ,
    UPDATE,
    DELETE,
    CRUD
}

enum class FieldType {
    STRING,
    LONG,
    INT,
    DATE,
    DOUBLE
}

enum class FieldReplaceType {
    REPLACE,
    RIGHT
}

enum class ExceptionsCode(val code: Int) {
    USERNAME_NOTFOUND(100),
    ALREADY_DELETED(101),
    ID_NOT_FOUND(102),
    USERNAME_OR_PASSWORD_INCORRECT(103),
    FILE_FORMAT_NOT_FOUND_EXCEPTION(104),
    SAMPLE_NOT_FOUND(105),
    USERNAME_NOT_FOUND(106),
    USERNAME_ALREADY_EXISTS(107),
    SAMPLE_ALREADY_EXISTS(108),
    SAMPLE_CONFLICT(109),
    DOCUMENT_ALREADY_EXISTS(110),
    ORGANIZATION_ALREADY_EXISTS(111),
    USER_PASSWORD_CONFLICT(112),
    ACCESS_DENIED(113),
    ORGANIZATION_ID_IS_NULL(114),
    FILE_NOT_READY(115),
    PATH_NULL(116),
    FILE_NOT_FOUND(117),
    SAMPLE_DELETED(118),
    REQUIRED_VALUE(118),
//    FILE_FORMAT_NOT_FOUND_EXCEPTION(105),

}