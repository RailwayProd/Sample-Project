package uz.shukrullaev.com.sample


import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 5:01 pm
 */

@ControllerAdvice
class GlobalExceptionHandler(
    private val errorMessageSource: ResourceBundleMessageSource,
) {

    @ExceptionHandler(ExceptionUtil::class)
    fun handleAppExceptions(ex: ExceptionUtil): ResponseEntity<Any?> {
        return when (ex) {

            is ObjectIdNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.id))

            is ObjectIdsNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.id))

            is UserNameFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.username))

            is AlreadyDeletedException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.id))

            is UsernameOrPasswordIncorrect -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.username, ex.password))

            is FileFormatNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.formatName))

            is SampleNameNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.name))

            is SampleNameAlreadyExistsException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.name))

            is SampleConflictException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.sampleId, ex.valueId))


            is DocumentNameAlreadyExistsException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.name))

            is UsernameNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.requestDTO))

            is UsernameAlreadyException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.username))

            is OrganizationAlreadyExistsException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.identifierNumber))

            is UserPasswordConflictException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.oldPassword, ex.newPassword))

            is AccessDeniedException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            is AllowContractCreationException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            is OrganizationIdIsNullException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            is FileNotFoundException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            is PathNullException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            is FileNotReadyException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.infoStatus))

            is SampleDeletedException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.sampleId))

            is SampleValueIsRequiredException -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource, ex.sampleId, ex.value, ex.valueId))
        }
    }
}
