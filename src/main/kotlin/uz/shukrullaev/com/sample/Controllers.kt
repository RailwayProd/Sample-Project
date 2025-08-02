package uz.shukrullaev.com.sample

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import uz.shukrullaev.com.sample.*


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 5:41 pm
 */

@RestController
@RequestMapping("api/organizations")
class OrganizationController(
    private val organizationService: OrganizationService,
) {
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    fun create(@RequestBody dto: OrganizationRequestDto): OrganizationResponseDto {
        return organizationService.create(dto)
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    fun update(@PathVariable id: Long, @RequestBody dto: OrganizationRequestDto) =
        organizationService.update(id, dto)

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_DIRECTOR')")
    fun get(@PathVariable id: Long) = organizationService.get(id)

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_DIRECTOR')")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        organizationService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    fun getAll(
        @RequestParam(required = false) organizationId: Long? = null,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false) orderDirection: OrderDirection? = null,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = organizationService.getAll(organizationId, search, orderDirection, pageable)


}

@RestController
@RequestMapping("api/users")
class UserController(
    private val userService: UserService,
    private val authService: AuthService,
) {
    @PostMapping("auth/registration")
    fun registration(@RequestBody userRequestDto: UserRequestDto): UserResponseDto {
        return authService.registration(userRequestDto)
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR', 'ROLE_ADMIN')")
    fun create(@RequestBody userRequestDto: UserRequestDto): UserResponseDto {
        return userService.create(userRequestDto)
    }

    @PostMapping("auth/login")
    fun login(@RequestParam username: String, @RequestParam password: String): TokenDTO? {
        return authService.login(username, password)
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR', 'ROLE_ADMIN')")
    fun update(@PathVariable id: Long, @RequestBody dto: UserRequestForUpdateDto) = userService.update(id, dto)

    @GetMapping("{id}")
    fun get(@PathVariable id: Long) = userService.get(id)

    @GetMapping("me")
    fun me() = authService.me()


    @PostMapping("set-password")
    fun changePassword(@RequestParam oldPassword: String, @RequestParam newPassword: String) =
        authService.changePassword(oldPassword, newPassword)
    @PostMapping("set-password-admin")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    fun changePasswordForAdmin(@RequestParam username: String, @RequestParam password: String) =
        authService.changePasswordForAdmin(username, password)

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR', 'ROLE_ADMIN')")
    fun delete(@PathVariable id: Long) =
        userService.delete(id)

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR', 'ROLE_ADMIN')")
    fun getAll(
        @RequestParam search: String?,
        @RequestParam orderDirection: OrderDirection?,
        @RequestParam organizationId: Long?,
        @RequestParam status: Status?,
        @RequestParam role: Role?,
        pageable: Pageable,
    ) = userService.getAll(search, orderDirection, organizationId, status, role, pageable)

    @GetMapping("sample-permissions/{targetId}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun getAllSamplePermissions(
        @PathVariable targetId: Long,
        @RequestParam search: String?,
        @RequestParam orderDirection: OrderDirection?,
        pageable: Pageable,
    ) = userService.getAllSamplePermissions(pageable, search, orderDirection, targetId)

    @GetMapping("documentation-permissions/{targetId}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun getAllDocumentationPermissions(
        @PathVariable targetId: Long,
        @RequestParam search: String?,
        @RequestParam orderDirection: OrderDirection?,
        pageable: Pageable,
    ) = userService.getAllDocumentationPermissions(pageable, search, orderDirection, targetId)
}

@RestController
@RequestMapping("api/samples")
class SampleController(
    private val sampleService: SampleService,
) {

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR', 'ROLE_OPERATOR')")
    fun create(@RequestBody dto: SampleRequestDto): ResponseEntity<SampleResponseDto> {
        return ResponseEntity.ok(sampleService.create(dto))
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @samplePermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).UPDATE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: SampleRequestDto,
    ): SampleResponseDto {
        return sampleService.update(id, dto)
    }

    @GetMapping("{id}")
    fun get(@PathVariable id: Long): SampleResponseDto {
        return sampleService.get(id)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @samplePermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).DELETE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        sampleService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getAll(
        pageable: Pageable,
        @RequestParam search: String?,
        @RequestParam orderDirection: OrderDirection?,
    ): Page<SampleResponseDto> {
        return sampleService.getAll(search, orderDirection, pageable)
    }

    @PostMapping("{sampleId}/fields")
    fun addField(
        @PathVariable sampleId: Long,
        @RequestBody dto: SampleFieldRequestDto,
    ): SampleFieldResponseDto {
        return sampleService.addField(sampleId, dto)
    }

    @PutMapping("fields/{fieldId}")
    fun updateField(
        @PathVariable fieldId: Long,
        @RequestBody dto: SampleFieldRequestDto,
    ): SampleFieldResponseDto {
        return sampleService.updateField(fieldId, dto)
    }

    @DeleteMapping("fields/{fieldId}")
    fun deleteField(@PathVariable fieldId: Long): ResponseEntity<Void> {
        sampleService.deleteField(fieldId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("{sampleId}/fields")
    fun getFieldsBySampleId(@PathVariable sampleId: Long): List<SampleFieldResponseDto> {
        return sampleService.getFieldsBySampleId(sampleId)
    }

    @PostMapping("upload", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR','ROLE_OPERATOR')")
    fun uploadSampleFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("name") name: String,
    ): SampleResponseDto {
        return sampleService.uploadSampleFile(file, name)
    }

    @PutMapping("update-file/{id}", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @samplePermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).UPDATE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun updateSampleFile(
        @PathVariable id: Long,
        @RequestParam("file") file: MultipartFile?,
        @RequestParam("name") name: String,
    ): SampleResponseDto {
        return sampleService.updateSampleFile(file, id, name)
    }

    @GetMapping("show-sample/{id}")
    fun getSampleFile(@PathVariable id: Long) = sampleService.getSampleFile(id)

    @PutMapping("update-fields/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @samplePermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).UPDATE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun updateFields(@PathVariable id: Long, @RequestBody sampleFields: List<SampleFieldRequestDto>) =
        sampleService.updateFields(id, sampleFields)
}

@RestController
@RequestMapping("api/documentations")
class DocumentationController(
    private val documentationService: DocumentationService,
) {

        @PostMapping("download/{docId}")
    fun downloadFile(
        @PathVariable docId: Long,
        @RequestParam format: String,
    ): ResponseEntity<ByteArray> {
        val fileBytes = documentationService.downloadFile(docId, format)
        val extension = format.lowercase()
        val fileName = "document_$docId.$extension"
        val contentType = getMediaType(extension)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
            .contentType(contentType)
            .body(fileBytes)
    }


    @PostMapping
    fun create(@RequestBody dto: DocumentationRequestDto): DocumentationResponseDto {
        return documentationService.create(dto)
    }


    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @documentationPermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).UPDATE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: DocumentationRequestDto,
    ): DocumentationResponseDto {
        return documentationService.update(id, dto)
    }

    @GetMapping("{id}")
    fun get(@PathVariable id: Long): DocumentationResponseDto {
        return documentationService.get(id)
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERATOR') && @documentationPermissionService.hasPermission(authentication.principal.id, #id, T(uz.shukrullaev.com.sample.Permissions).DELETE) || hasAnyAuthority('ROLE_DIRECTOR')")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        documentationService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getAll(
        @RequestParam search: String?,
        @RequestParam organizationId: Long?,
        @RequestParam orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DocumentationResponseDto> {
        return documentationService.getAll(search, organizationId, orderDirection, pageable)
    }

    @GetMapping("sample/{sampleId}")
    fun getBySampleId(
        @PathVariable sampleId: Long,
        pageable: Pageable,
    ): Page<DocumentationResponseDto> {
        return documentationService.getBySampleId(sampleId, pageable)
    }

    @PostMapping("{documentationId}/values/{fieldId}")
    fun addValue(
        @PathVariable documentationId: Long,
        @PathVariable fieldId: Long,
        @RequestParam value: String,
    ): DocumentationValueResponseDto {
        return documentationService.addValue(documentationId, fieldId, value)
    }

    @PutMapping("values/{valueId}")
    fun updateValue(
        @PathVariable valueId: Long,
        @RequestParam newValue: String,
    ): DocumentationValueResponseDto {
        return documentationService.updateValue(valueId, newValue)
    }

    @GetMapping("{documentationId}/values")
    fun getValuesByDocumentationId(
        @PathVariable documentationId: Long,
    ): List<DocumentationValueResponseDto> {
        return documentationService.getValuesByDocumentationId(documentationId)
    }

    @GetMapping("{documentationId}/audit")
    fun getAuditInfo(@PathVariable documentationId: Long): AuditInfoDto {
        return documentationService.getAuditInfo(documentationId)
    }

}

@RestController
@RequestMapping("api/download-info")
class DownloadInfoController(
    private val downloadInfoService: DownloadInfoService,
    private val sseEmitterService: SseEmitterService
) {

    @GetMapping
    fun getAll(
        @RequestParam status: FileDownloadStatus?,
        @RequestParam orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DownloadInfoResponseDto> {
        return downloadInfoService.getAll(status, orderDirection, pageable)
    }

    @GetMapping("{infoId}/documents")
    fun getDocumentsByDownloadInfoId(
        @PathVariable infoId: Long
    ): List<DocumentationResponseDto> {
        return downloadInfoService.getDocumentsByDownloadInfoId(infoId)
    }

//    @GetMapping("{id}/status-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
//    fun streamStatus(@PathVariable id: Long): SseEmitter {
//        return sseEmitterService.createEmitter(id)
//    }

    @GetMapping("{infoId}/download")
    fun downloadFilesByDownloadInfoId(
        @PathVariable infoId: Long,
    ): ResponseEntity<ByteArray> {
        val zipBytes = downloadInfoService.downloadFilesByDownloadInfoId(infoId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documents_$infoId.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zipBytes)
    }

    @PostMapping("create")
    fun createDownloadInfo(
        @RequestBody request: CreateDownloadInfoRequest // documentationIds + format
    ): DownloadInfoResponseDto {
        return downloadInfoService.createDownloadInfo(request)
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamAll(): SseEmitter {
        return sseEmitterService.register()
    }
}

@RestController
@RequestMapping("api/documentation-permissions")
class DocumentationPermissionController(
    private val service: DocumentationPermissionServiceImpl
) {
    @PostMapping("grant")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun grantPermission(
        @RequestBody dto: List<DocumentationPermissionRequestDto>
    ) = service.grantPermission(dto)

    @DeleteMapping("remove-permission/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun removePermissions(@PathVariable id: Long) {
        service.removeById(id)
    }

}

@RestController
@RequestMapping("api/sample-permissions")
class SamplePermissionController(
    private val service: SamplePermissionServiceImpl
) {
    @PostMapping("grant")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun grantPermission(
        @RequestBody dto: List<SamplePermissionRequestDto>
    ) = service.grantPermission(dto)

    @DeleteMapping("remove-permission/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_DIRECTOR')")
    fun removePermissions(@PathVariable id: Long) {
        service.removeById(id)
    }
}


fun getMediaType(extension: String): MediaType = when (extension.lowercase()) {
    "pdf" -> MediaType.APPLICATION_PDF
    "docx" -> MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document")
    "txt" -> MediaType.TEXT_PLAIN
    "csv" -> MediaType("text", "csv")
    else -> MediaType.APPLICATION_OCTET_STREAM
}