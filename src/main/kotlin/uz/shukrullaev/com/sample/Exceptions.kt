package uz.shukrullaev.com.sample

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.*


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:54 pm
 */

sealed class ExceptionUtil(message: String? = null) : RuntimeException(message) {
    abstract fun exceptionType(): ExceptionsCode
    fun getErrorMessage(errorMessageSource: ResourceBundleMessageSource, vararg array: Any?): BaseMessage {
        return BaseMessage(
            exceptionType().code, errorMessageSource.getMessage(
                exceptionType().toString(), array, Locale(
                    LocaleContextHolder.getLocale().language
                )
            )
        )
    }
}

class ObjectIdNotFoundException(val id: Long? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ID_NOT_FOUND
}

class ObjectIdsNotFoundException(val id: List<Long?>? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ID_NOT_FOUND
}

class UserNameFoundException(val username: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USERNAME_NOTFOUND
}


class AlreadyDeletedException(val id: Long? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ALREADY_DELETED
}

class UsernameOrPasswordIncorrect(val username: String? = null, val password: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USERNAME_OR_PASSWORD_INCORRECT
}

class FileFormatNotFoundException(val formatName: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.FILE_FORMAT_NOT_FOUND_EXCEPTION
}

class SampleNameNotFoundException(val name: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.SAMPLE_NOT_FOUND
}

class SampleConflictException(val sampleId: Long? = null, val value: String? = null, val valueId: Long? = null) :
    ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.SAMPLE_CONFLICT
}

class SampleNameAlreadyExistsException(val name: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.SAMPLE_ALREADY_EXISTS
}

class DocumentNameAlreadyExistsException(val name: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.DOCUMENT_ALREADY_EXISTS
}

class UsernameNotFoundException(val requestDTO: Any) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USERNAME_NOT_FOUND
}

class UsernameAlreadyException(val username: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USERNAME_ALREADY_EXISTS
}

class OrganizationAlreadyExistsException(val identifierNumber: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ORGANIZATION_ALREADY_EXISTS
}

class UserPasswordConflictException(val oldPassword: String? = null, val newPassword: String) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USER_PASSWORD_CONFLICT
}

class AccessDeniedException : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ACCESS_DENIED
}

class AllowContractCreationException  : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ALLOW_CONTRACT_CREATION
}

class OrganizationIdIsNullException : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ORGANIZATION_ID_IS_NULL
}

class FileNotReadyException(val infoStatus: FileDownloadStatus? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.FILE_NOT_READY
}

class PathNullException : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.PATH_NULL
}

class FileNotFoundException : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.FILE_NOT_FOUND
}

class SampleDeletedException(val sampleId: Long? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.SAMPLE_DELETED
}

class SampleValueIsRequiredException(val sampleId: Long? = null, val value: String? = null, val valueId: Long?) :
    ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.REQUIRED_VALUE
}