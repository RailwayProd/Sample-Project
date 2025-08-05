package uz.shukrullaev.com.sample

import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant
import java.util.*

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:39 pm
 */

data class BaseMessage(val code: Int, val message: String?)


data class TokenDTO(
    val token: String?,
    val tokenCreateAt: Date?,
    val expiredTime: Date?,
)

data class AuditInfoDto(
    val createdBy: String?,
    val createdDate: Instant?,
    val updatedBy: String?,
    val updatedDate: Instant?,
)

data class OrganizationRequestDto(
    @field:NotBlank val name: String,
    @field:NotBlank val identifierNumber: String,
)

data class OrganizationResponseDto(
    val id: Long,
    val name: String,
    val identifierNumber: String,
    val users: List<UserShortDto> = emptyList()
)

data class OrganizationShortDto(
    val id: Long,
    val name: String,
)


fun OrganizationRequestDto.toEntity() = Organization(
    name = this.name,
    identifierNumber = this.identifierNumber,
)

fun Organization.toDTO() = OrganizationResponseDto(
    id = this.id!!,
    name = this.name,
    identifierNumber = this.identifierNumber,
)

fun Organization.toShortDTO() = OrganizationShortDto(
    id = this.id!!,
    name = this.name,
)

fun Organization.updateFrom(dto: OrganizationRequestDto) {
    name = dto.name
    identifierNumber = dto.identifierNumber
}


data class UserRequestDto(
    @field:NotBlank val fullName: String,
    @field:NotBlank val username: String,
    @field:NotBlank var password: String,
    var role: Role? = Role.OPERATOR,
    val organizationId: Long? = null,
)

data class UserRequestForUpdateDto(
    val fullName: String? = null,
    val username: String? = null,
    val role: Role?,
    val organizationId: Long? = null,
    val status: Status? = Status.ACTIVE,
)

data class UserResponseDto(
    val id: Long,
    val fullName: String?,
    val username: String?,
    val role: Role,
    val organization: OrganizationShortDto?,
    val status: Status,

    )

data class UserShortDto(
    val id: Long,
    val fullName: String?,
    val username: String?,
    val role: Role,
    val status: Status,
)


fun UserRequestDto.toEntity(organization: Organization? = null) = User(
    fullName = this.fullName,
    username = this.username,
    password = this.password,
    role = role!!,
    organization = organization
)

fun User.toDTO() = UserResponseDto(
    id = this.id!!,
    fullName = this.fullName,
    username = this.username,
    role = this.role,
    organization = this.organization?.toShortDTO(),
    status = status!!
)


data class SampleRequestDto(
    @field:NotBlank val name: String,
    @field:NotBlank var pathFile: String,
    @field:NotBlank var templateHas: String,
)

data class SampleResponseDto(
    val id: Long,
    val name: String,
    val sampleFields: List<SampleFieldResponseDto>? = null,
    val permissions: Set<Permissions> = emptySet()
)

fun SampleRequestDto.toEntity(owner: User, organization: Organization) = Sample(
    name = this.name,
    filePath = pathFile,
    templateHash = templateHas,
    owner = owner,
    organization = organization
)

fun Sample.toDTO() = SampleResponseDto(
    id = this.id!!,
    name = this.name,
    sampleFields = this.fields.map { it.toDTO() }
)

fun Sample.toDTO(currentUser: User) = SampleResponseDto(
    id = this.id!!,
    name = this.name,
    sampleFields = this.fields.map { it.toDTO() },
    permissions = this.permissions
        .filter { it.user.role != Role.DIRECTOR && it.user.id == currentUser.id && !it.deleted }
        .map { it.permission }
        .toSet()
)
data class SampleFieldRequestDto(
    val id: Long? = null,
    @field:NotBlank val keyName: String,
    val fieldType: FieldType,
    val isRequired: Boolean,
    val sampleId: Long,
    val fieldReplaceType: FieldReplaceType,
)

data class SampleFieldResponseDto(
    val id: Long,
    val keyName: String,
    val fieldType: FieldType,
    val isRequired: Boolean,
    val fieldReplaceType: FieldReplaceType,
)

fun SampleFieldRequestDto.toEntity(sample: Sample) = SampleField(
    keyName = this.keyName,
    fieldType = this.fieldType,
    isRequired = this.isRequired,
    fieldReplaceType = this.fieldReplaceType,
    sample = sample
)

fun SampleField.toDTO() = SampleFieldResponseDto(
    id = this.id!!,
    keyName = this.keyName,
    fieldType = this.fieldType,
    isRequired = this.isRequired,
    fieldReplaceType = this.fieldReplaceType
)

data class DocumentationRequestDto(
    val id: Long? = null,
    @field:NotBlank val name: String,
    val sampleId: Long,
    val values: List<DocumentationValueRequestDto> = emptyList(),

    )

data class DocumentationResponseDto(
    val id: Long,
    val name: String,
    val sample: SampleResponseDto,
    val values: List<DocumentationValueResponseDto> = emptyList(),
    val permissions: List<Permissions> = emptyList()
)


fun DocumentationRequestDto.toEntity(
    sample: Sample,
    owner: User,
    organization: Organization
) = Documentation(
    name = this.name,
    sample = sample,
    owner = owner,
    organization = organization

)

fun Documentation.toDTO() = DocumentationResponseDto(
    id = this.id!!,
    name = this.name,
    sample = this.sample.toDTO(),
    values = this.values.map { it.toDTO() }
)

fun Documentation.toDTO(currentUser: User): DocumentationResponseDto {
    return DocumentationResponseDto(
        id = this.id!!,
        name = this.name,
        sample = this.sample.toDTO(),
        values = this.values.map { it.toDTO() },
        permissions = this.permissions
            .filter { it.user.role != Role.DIRECTOR && it.user.id == currentUser.id && !it.deleted }
            .map { it.permission }
    )
}

data class DocumentationValueRequestDto(
    @field:NotBlank val value: String?,
    val fieldId: Long,
    val documentationId: Long,
)

data class DocumentationValueResponseDto(
    val id: Long,
    val value: String?,
    val field: SampleFieldResponseDto,
)

fun DocumentationValueRequestDto.toEntity(
    field: SampleField,
    documentation: Documentation,
) = DocumentationValue(
    value = this.value,
    field = field,
    documentation = documentation
)

fun DocumentationValue.toDTO() = DocumentationValueResponseDto(
    id = this.id!!,
    value = this.value,
    field = this.field.toDTO()
)


data class DownloadInfoResponseDto(
    val id: Long,
    val format: String,
    val status: FileDownloadStatus?,
    val documentationIds: List<Long>,
    val createdDate: Instant?
)

fun DownloadInfo.toDTO(): DownloadInfoResponseDto =
    DownloadInfoResponseDto(
        id = this.id!!,
        format = this.format,
        status = this.status,
        documentationIds = this.documentations.mapNotNull { it.id },
        createdDate = this.createdDate
    )

data class CreateDownloadInfoRequest(
    val documentationIds: List<Long>,
    val format: String
)

interface BasePermissionRequestDto {
    val userId: Long
    val targetId: Long
    val permissions: List<Permissions>
}

data class DocumentationPermissionRequestDto(
    override val userId: Long,
    override val targetId: Long, // renamed from documentationId
    override val permissions: List<Permissions>
) : BasePermissionRequestDto

data class DocumentationPermissionResponseDto(
    val id: Long?,
    val userId: Long,
    val documentationId: Long,
    val permission: Permissions
)

fun DocumentationPermission.toDTO() = DocumentationPermissionResponseDto(
    id = id,
    userId = user.id!!,
    documentationId = documentation.id!!,
    permission = permission
)

data class SamplePermissionRequestDto(
    override val userId: Long,
    override val targetId: Long,
    override val permissions: List<Permissions>
) : BasePermissionRequestDto

data class SamplePermissionResponseDto(
    val id: Long?,
    val userId: Long,
    val sampleId: Long,
    val permission: Permissions
)

fun SamplePermission.toDTO() = SamplePermissionResponseDto(
    id = id,
    userId = user.id!!,
    sampleId = sample.id!!,
    permission = permission
)

data class UserSamplePermissionDto(
    val id: Long,
    val fullName: String?,
    val username: String?,
    val role: Role,
    val samplePermissions: MutableSet<Permissions>
)

data class UserDocumentationPermissionDTO(
    val id: Long,
    val fullName: String?,
    val username: String?,
    val role: Role,
    val documentationPermissions: MutableSet<Permissions>
)

fun User.toDTODocPermission(targetId: Long): UserDocumentationPermissionDTO =
    UserDocumentationPermissionDTO(
        id = this.id!!,
        fullName = this.fullName,
        username = this.username,
        role = this.role,
        documentationPermissions = this.documentationPermission
            .filter { it.documentation.id == targetId }
            .map { it.permission }
            .toMutableSet()
    )

fun User.toDTOSamplePermission(targetId: Long): UserSamplePermissionDto =
    UserSamplePermissionDto(
        id = this.id!!,
        fullName = this.fullName,
        username = this.username,
        role = this.role,
        samplePermissions = this.samplePermissions
            .filter { it.sample.id == targetId }
            .map { it.permission }
            .toMutableSet()
    )

data class CustomUserDetails(
    val id: Long,
    private val username: String,
    private val authoritiesList: Collection<GrantedAuthority>
) : UserDetails {

    override fun getUsername() = username
    override fun getAuthorities() = authoritiesList
    override fun getPassword() = null
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
}

