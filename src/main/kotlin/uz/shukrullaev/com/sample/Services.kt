package uz.shukrullaev.com.sample

import jakarta.persistence.criteria.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.*
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:47 pm
 */
fun getUserName(): String = SecurityContextHolder.getContext().authentication.name
fun getUserId(): Long {
    val principal = SecurityContextHolder.getContext().authentication.principal
    return if (principal is CustomUserDetails) {
        principal.id
    } else {
        throw IllegalStateException("User ID not found in security context")
    }
}

fun getUserRoles(): List<Role> {
    val authorities = SecurityContextHolder.getContext().authentication.authorities
    return authorities.mapNotNull { Role.fromAuthority(it.authority) }
}

@Suppress("UNCHECKED_CAST")
abstract class AbstractPermissionService<
        T : BaseEntity,
        E : AbstractPermission<T>,
        RequestDto : BasePermissionRequestDto,
        ResponseDto
        >(
    private val permissionRepository: JpaRepository<E, Long>,
    private val targetFetcher: (Set<Long>) -> Map<Long, T>,
    private val userFetcher: (Set<Long>) -> Map<Long, User>,
    private val toEntity: (T, User, Permissions) -> E,
    private val toDto: (E) -> ResponseDto
) {

    open fun removeById(id: Long) {
        permissionRepository.deleteById(id)
    }

    open fun grantPermission(dtoList: List<RequestDto>) {
        val targetIds = dtoList.map { it.targetId }.toSet()
        val userIds = dtoList.map { it.userId }.toSet()

        val targets = targetFetcher(targetIds)
        val users = userFetcher(userIds)

        val newEntities = mutableListOf<E>()
        val toDelete = mutableListOf<E>()

        dtoList.forEach { dto ->
            val target = targets[dto.targetId] ?: throw ObjectIdNotFoundException(dto.targetId)
            val user = users[dto.userId] ?: throw ObjectIdNotFoundException(dto.userId)

            val requested = dto.permissions.toMutableSet()
            if (Permissions.DELETE in requested || Permissions.UPDATE in requested) {
                requested += Permissions.READ
            }

            val existing = (permissionRepository as JpaRepository<E, *>)
                .findAll()
                .filterIsInstance<AbstractPermission<*>>()
                .filter { it.user.id == user.id && (it.getTarget() as BaseEntity).id == target.id }
                .map { it as E }

            val existingPerms = existing.map { it.permission }.toSet()

            val toAdd = requested.filter { it !in existingPerms }
            newEntities += toAdd.map { perm -> toEntity(target, user, perm) }

            toDelete += existing.filter { it.permission !in requested }
        }

        if (toDelete.isNotEmpty()) permissionRepository.deleteAll(toDelete)
        if (newEntities.isNotEmpty()) permissionRepository.saveAll(newEntities) else emptyList()
    }


    abstract fun hasPermission(userId: Long, targetId: Long, permission: Permissions): Boolean
}

interface AuthService {
    fun login(username: String, password: String): TokenDTO?
    fun registration(userRequestDto: UserRequestDto): UserResponseDto
    fun me(): UserResponseDto?
    fun changePassword(oldPassword: String, newPassword: String)
    fun changePasswordForAdmin(username: String, password: String)
}

interface UserService {
    fun create(dto: UserRequestDto): UserResponseDto
    fun update(id: Long, dto: UserRequestForUpdateDto): UserResponseDto
    fun get(id: Long): UserResponseDto
    fun delete(id: Long)
    fun getAll(
        search: String?,
        orderDirection: OrderDirection?,
        organizationId: Long?,
        status: Status?,
        role: Role?,
        pageable: Pageable,
    ): Page<UserResponseDto>

    fun getAllSamplePermissions(
        pageable: Pageable,
        search: String?,
        orderDirection: OrderDirection?,
        sampleId: Long
    ): Page<UserSamplePermissionDto>

    fun getAllDocumentationPermissions(
        pageable: Pageable,
        search: String?,
        orderDirection: OrderDirection?,
        documentationId: Long,
    ): Page<UserDocumentationPermissionDTO>
}

interface OrganizationService {
    fun create(dto: OrganizationRequestDto): OrganizationResponseDto
    fun update(id: Long, dto: OrganizationRequestDto): OrganizationResponseDto
    fun get(id: Long): OrganizationResponseDto
    fun delete(id: Long)
    fun getAll(
        organizationId: Long?,
        search: String?,
        orderDirection: OrderDirection?, pageable: Pageable,
    ): Page<OrganizationResponseDto>
}

interface SampleService {
    fun create(dto: SampleRequestDto): SampleResponseDto
    fun update(id: Long, dto: SampleRequestDto): SampleResponseDto
    fun get(id: Long): SampleResponseDto
    fun delete(id: Long)
    fun getAll(search: String?, orderDirection: OrderDirection?, pageable: Pageable): Page<SampleResponseDto>

    fun addField(sampleId: Long, dto: SampleFieldRequestDto): SampleFieldResponseDto
    fun updateField(fieldId: Long, dto: SampleFieldRequestDto): SampleFieldResponseDto

    fun updateFields(sampleId: Long, sampleFields: List<SampleFieldRequestDto>): SampleResponseDto
    fun deleteField(fieldId: Long)
    fun getFieldsBySampleId(sampleId: Long): List<SampleFieldResponseDto>
    fun uploadSampleFile(file: MultipartFile, name: String): SampleResponseDto
    fun updateSampleFile(
        file: MultipartFile?,
        sampleId: Long,
        name: String?,
        allowContractCreation: Boolean?
    ): SampleResponseDto

    fun getSampleFile(id: Long): ResponseEntity<ByteArray>
}

interface DocumentationService {
    fun downloadFile(documentationId: Long, format: String): ByteArray
    fun create(dto: DocumentationRequestDto): DocumentationResponseDto
    fun update(id: Long, dto: DocumentationRequestDto): DocumentationResponseDto
    fun get(id: Long): DocumentationResponseDto
    fun delete(id: Long)
    fun getAll(
        name: String?,
        organizationId: Long?,
        orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DocumentationResponseDto>

    fun getBySampleId(sampleId: Long, pageable: Pageable): Page<DocumentationResponseDto>
    fun addValue(documentationId: Long, fieldId: Long, value: String): DocumentationValueResponseDto
    fun updateValue(valueId: Long, newValue: String): DocumentationValueResponseDto
    fun getValuesByDocumentationId(documentationId: Long): List<DocumentationValueResponseDto>
    fun deleteValue(valueId: Long)
    fun getAuditInfo(documentationId: Long): AuditInfoDto

}

interface DownloadInfoService {
    fun getAll(
        status: FileDownloadStatus?,
        orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DownloadInfoResponseDto>

    fun getDocumentsByDownloadInfoId(downloadInfoId: Long): List<DocumentationResponseDto>
    fun downloadFilesByDownloadInfoId(downloadInfoId: Long): ByteArray

    fun createDownloadInfo(request: CreateDownloadInfoRequest): DownloadInfoResponseDto

    fun deleteById(infoId: Long)

}


@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val jwtUtil: JwtUtil,
    private val passwordEncoder: PasswordEncoder,
) : AuthService {
    override fun login(username: String, password: String): TokenDTO? {
        return listOfNotNull(
            checkByPasswordAndPassword(username, password),
        ).firstOrNull() ?: throw UsernameOrPasswordIncorrect(username)
    }

    override fun registration(userRequestDto: UserRequestDto): UserResponseDto =
        userRequestDto.username
            .takeIf { username -> userRepository.findByUsernameAndDeletedFalse(username).isEmpty }
            ?.let {
                userRequestDto.organizationId
                    ?.let(organizationRepository::findByIdAndDeletedFalse)
                    ?: userRequestDto.organizationId?.let { throw ObjectIdNotFoundException(it) }
            }
            .let { organization ->
                userRequestDto.toEntity(organization)
                    .apply { password = encodePassword(userRequestDto.password) }
            }
            .let(userRepository::save)
            .toDTO()


    private fun checkByPasswordAndPassword(username: String, password: String): TokenDTO {
        return username.let {
            userRepository.findByUsernameAndDeletedFalse(it)
                .orElseThrow { UsernameOrPasswordIncorrect(it) }
                .let { user ->
                    (user.status != Status.ACTIVE).runIfTrue { throw UsernameOrPasswordIncorrect(username, password) }
                    (passwordEncoder.matches(password, user.password)).runIfFalse {
                        throw UsernameOrPasswordIncorrect(username, password)
                    }
                    (user.role != Role.ADMIN && organizationRepository.existsByIdAndDeletedFalse(user.organization!!.id!!) == false).runIfTrue { throw UsernameOrPasswordIncorrect() }
                    jwtUtil.generateToken(user)
                }

        }
    }

    override fun me(): UserResponseDto? {
        return userRepository.findByUsernameAndDeletedFalse(getUserName())
            .getOrNull()
            ?.toDTO()
            ?: throw UsernameNotFoundException(getUserName())
    }

    override fun changePassword(oldPassword: String, newPassword: String) {
        userRepository.findByUsernameAndDeletedFalse(getUserName())
            .orElseThrow { UsernameNotFoundException(getUserName()) }
            .also { validatePasswords(oldPassword, newPassword, it) }
            .run { updatePassword(newPassword) }
            .let(userRepository::save)
    }

    override fun changePasswordForAdmin(username: String, password: String) {
        userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow { UserNameFoundException(username) }
            .apply { this.password = passwordEncoder.encode(password) }
            .also { userRepository.save(it) }
    }

    private fun validatePasswords(old: String, new: String, user: User) {
        if (old == new) {
            throw UserPasswordConflictException(old, new)
        }
        if (!matches(old, user.password)) {
            throw UsernameOrPasswordIncorrect(getUserName(), old)
        }
    }

    private fun User.updatePassword(newPassword: String): User = apply {
        password = encodePassword(newPassword)
    }


    fun encodePassword(password: String): String {
        return passwordEncoder.encode(password)
    }

    fun matches(password: String, encodedPassword: String): Boolean {
        return passwordEncoder.matches(password, encodedPassword)
    }

}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val authService: AuthServiceImpl,
    private val userContextService: UserContextService,
) : UserService {

    override fun create(dto: UserRequestDto): UserResponseDto {
        val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

        if (userRepository.findByUsername(dto.username).isPresent)
            throw UsernameAlreadyException(dto.username)

        val organization = resolveOrganization(dto.organizationId, role, getUserName())

        return dto.copy(
            password = authService.encodePassword(dto.password),
            role = if (role == Role.DIRECTOR) Role.OPERATOR else dto.role
        ).toEntity(organization)
            .let(userRepository::save)
            .toDTO()
    }


    @Transactional
    override fun update(id: Long, dto: UserRequestForUpdateDto): UserResponseDto {
        val currentUsername = getUserName()
        val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

        val organization = resolveOrganization(dto.organizationId, role, currentUsername)
        val currentUser =
            userRepository.findByUsernameAndDeletedFalse(currentUsername).orElseThrow { UserNameFoundException() }
        if (role != Role.DIRECTOR && currentUser!!.id != id) throw AccessDeniedException()
        return userRepository.findByIdAndDeletedFalse(id)
            ?.apply {
                dto.fullName?.let { fullName = it }
                if (dto.username != null && this.username != dto.username)
                    userRepository.findByUsername(dto.username).isPresent.runIfTrue { throw UsernameAlreadyException(dto.username) }
                dto.username?.let { username = it }


                this.role = when (role) {
                    Role.ADMIN -> dto.role ?: this.role

                    Role.DIRECTOR -> when {
                        dto.role == null && this.id == currentUser.id -> this.role
                        dto.role != null && dto.role != Role.ADMIN -> dto.role
                        else -> Role.OPERATOR
                    }

                    Role.OPERATOR -> Role.OPERATOR
                }


                status = dto.status ?: status
                this.organization = organization
            }
            ?.let(userRepository::save)
            ?.toDTO()
            ?: throw ObjectIdNotFoundException(id)
    }


    @Transactional(readOnly = true)
    override fun get(id: Long): UserResponseDto {
        val user = userRepository.findByIdAndDeletedFalse(id)
            ?: throw ObjectIdNotFoundException(id)
        return user.toDTO()
    }

    @Transactional
    override fun delete(id: Long) {
        userRepository.trash(id)
            ?: throw AlreadyDeletedException(id)
    }

    @Transactional(readOnly = true)
    override fun getAll(
        search: String?,
        orderDirection: OrderDirection?,
        organizationId: Long?,
        status: Status?,
        role: Role?,
        pageable: Pageable,
    ): Page<UserResponseDto> {
        val orgId = userContextService.getCurrentOrganizationId(organizationId)
        val specification = createSpecification(search, orderDirection, orgId, status, role)
        return userRepository.findAll(specification, pageable).map { it.toDTO() }
    }

    @Transactional(readOnly = true)
    override fun getAllSamplePermissions(
        pageable: Pageable,
        search: String?,
        orderDirection: OrderDirection?,
        sampleId: Long
    ): Page<UserSamplePermissionDto> {
        val orgId = userContextService.getCurrentOrganizationId(null)
        val spec = createPermissionSpecification<SamplePermission>(
            "samplePermissions",
            search,
            orgId,
            orderDirection,
        )
        return userRepository.findAll(spec, pageable).toDTOSamplePermission(sampleId)
    }

    @Transactional(readOnly = true)
    override fun getAllDocumentationPermissions(
        pageable: Pageable,
        search: String?,
        orderDirection: OrderDirection?,
        documentationId: Long
    ): Page<UserDocumentationPermissionDTO> {
        val orgId = userContextService.getCurrentOrganizationId(null)
        val spec = createPermissionSpecification<DocumentationPermission>(
            "documentationPermission",
            search,
            orgId,
            orderDirection,
        )
        return userRepository.findAll(spec, pageable).toDTODocPermission(documentationId)
    }


    private fun resolveOrganization(
        organizationId: Long?,
        role: Role,
        currentUsername: String,
    ): Organization = when (role) {
        Role.ADMIN -> organizationId
            ?.let { organizationRepository.findByIdAndDeletedFalse(it) }
            ?: throw ObjectIdNotFoundException(organizationId)

        Role.DIRECTOR -> getOrganizationForOperatorAndDirector(currentUsername)

        Role.OPERATOR -> getOrganizationForOperatorAndDirector(currentUsername)
    }

    private fun getOrganizationForOperatorAndDirector(currentUsername: String): Organization =
        userRepository.findByUsername(currentUsername)
            .map { user ->
                val orgId = user.organization?.id ?: throw OrganizationIdIsNullException()
                organizationRepository.findByIdAndDeletedFalse(orgId)
                    ?: throw OrganizationIdIsNullException()
            }
            .orElseThrow { OrganizationIdIsNullException() }


    private fun createSpecification(
        search: String?,
        orderDirection: OrderDirection?,
        organizationId: Long?,
        status: Status?,
        role: Role?,
    ): Specification<User> {
        return Specification { from, query, builder ->
            val predicates = mutableListOf<Predicate>()

            predicates += builder.equal(from.get<Boolean>("deleted"), false)

            organizationId?.let {
                predicates += builder.equal(from.get<Organization>("organization").get<Long>("id"), it)
            }
            status?.let {
                predicates += builder.equal(from.get<String>("status"), status)
            }
            role?.let {
                predicates += builder.equal(from.get<String>("role"), role)
            }

            search?.let {
                val fullName = from.get<String>("fullName")
                val username = from.get<String>("username")
                val concat = builder.concat(fullName, username)
                predicates += builder.like(builder.lower(concat), "%${it.trim().lowercase()}%")
            }

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)

            builder.and(*predicates.toTypedArray())
        }
    }

    private fun <P : AbstractPermission<*>> createPermissionSpecification(
        permissionPath: String,
        search: String?,
        organizationId: Long?,
        orderDirection: OrderDirection?,
    ): Specification<User> {
        return Specification { from, query, builder ->

            val permissionJoin = from.join<User, P>(permissionPath, JoinType.LEFT)

            if (query?.resultType != Long::class.java && query?.resultType != java.lang.Long::class.java) {
                query?.groupBy(from)
                query?.orderBy(builder.desc(builder.count(permissionJoin.get<Long>("id"))))
            }

            val predicates = mutableListOf<Predicate>()
            predicates += builder.equal(from.get<Boolean>("deleted"), false)

            organizationId?.let {
                predicates += builder.equal(from.get<Organization>("organization").get<Long>("id"), it)
            }

            search?.takeIf { it.isNotBlank() }?.let {
                val pattern = "%${it.trim().lowercase()}%"
                predicates += builder.or(
                    builder.like(builder.lower(from.get("fullName")), pattern),
                    builder.like(builder.lower(from.get("username")), pattern)
                )
            }

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)

            builder.and(*predicates.toTypedArray())
        }
    }


}

@Service
class OrganizationServiceImpl(
    private val organizationRepository: OrganizationRepository,
) : OrganizationService {

    @Transactional
    override fun create(dto: OrganizationRequestDto): OrganizationResponseDto =
        dto.ensureIdentifierIsUnique()
            .toEntity()
            .let(organizationRepository::save)
            .toDTO()


    @Transactional
    override fun update(id: Long, dto: OrganizationRequestDto): OrganizationResponseDto =
        organizationRepository.findByIdAndDeletedFalse(id)
            ?.also { dto.ensureIdentifierIsUnique(it.identifierNumber) }
            ?.apply { updateFrom(dto) }
            ?.let(organizationRepository::save)
            ?.toDTO()
            ?: throw ObjectIdNotFoundException(id)

    @Transactional(readOnly = true)
    override fun get(id: Long): OrganizationResponseDto {
        val organization = organizationRepository.findByIdAndDeletedFalse(id)
            ?: throw ObjectIdNotFoundException(id)

        return organization.toDTO()
    }

    @Transactional
    override fun delete(id: Long) {
        organizationRepository.trash(id)
            ?: throw AlreadyDeletedException(id)
    }

    @Transactional(readOnly = true)
    override fun getAll(
        organizationId: Long?,
        search: String?,
        orderDirection: OrderDirection?, pageable: Pageable,
    ): Page<OrganizationResponseDto> {
        val userRoles = getUserRoles().firstOrNull() ?: throw AccessDeniedException()
        val specification: Specification<Organization> = if (userRoles == Role.ADMIN) {
            createSpecification(search, orderDirection)
        } else throw AccessDeniedException()
        return specification.let { organizationRepository.findAll(it, pageable) }
            .map { it.toDTO() }
    }


    private fun createSpecification(
        search: String?,
        orderDirection: OrderDirection?,
    ): Specification<Organization> {
        return Specification { from, query, builder ->
            val predicates = mutableListOf<Predicate>()

            predicates.add(builder.equal(from.get<Boolean>("deleted"), false))

            search?.let {
                val name = from.get<String>("name")
                val identifierNumber = from.get<String>("identifierNumber")
                val concat = builder.concat(name, identifierNumber)
                predicates += builder.like(builder.lower(concat), "%${it.trim().lowercase()}%")
            }

            query?.distinct(true)

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)

            builder.and(*predicates.toTypedArray())
        }
    }


    private fun OrganizationRequestDto.ensureIdentifierIsUnique(currentNumber: String? = null): OrganizationRequestDto {
        if (identifierNumber != currentNumber &&
            organizationRepository.existsByIdentifierNumberAndDeletedFalse(identifierNumber)
        ) {
            throw OrganizationAlreadyExistsException(identifierNumber)
        }
        return this
    }


}

@Service
class SampleServiceImpl(
    private val sampleRepository: SampleRepository,
    private val sampleFieldRepository: SampleFieldRepository,
    private val extractors: List<TextExtractor>,
    private val extractorFieldExecutor: ExtractorFieldExecutor,
    private val fileUtils: FileUtils,
    private val userRepository: UserRepository,
    private val userContextService: UserContextService,
) : SampleService {

    @Transactional
    override fun create(dto: SampleRequestDto): SampleResponseDto {
        val userName = getUserName()
        val user: User = getOwner(userName)

        val sample = dto.toEntity(user, user.organization!!)
        return sampleRepository.save(sample).toDTO()
    }


    @Transactional
    override fun update(id: Long, dto: SampleRequestDto): SampleResponseDto {
        val sample = sampleRepository.findByIdAndDeletedFalse(id)
            ?: throw ObjectIdNotFoundException(id)

        sample.name = dto.name
        return sampleRepository.save(sample).toDTO()
    }


    @Transactional(readOnly = true)
    override fun get(id: Long): SampleResponseDto {
        val sample = sampleRepository.findByIdAndDeletedFalse(id)
            ?: throw ObjectIdNotFoundException(id)

        return sample.toDTO()
    }

    @Transactional
    override fun delete(id: Long) {
        sampleRepository.trash(id)
            ?: throw AlreadyDeletedException(id)
    }

    @Transactional(readOnly = true)
    override fun getAll(
        search: String?,
        orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<SampleResponseDto> {
        val user = getOwner(getUserName())
        val role = getUserRoles().first()
        val orgId = userContextService.getCurrentOrganizationId(null)

        val spec = createSpecification(
            role = role,
            user = user,
            organizationId = orgId,
            search = search,
            orderDirection = orderDirection
        )

        return sampleRepository.findAll(spec, pageable).map { it.toDTO(user) }
    }


    @Transactional
    override fun addField(sampleId: Long, dto: SampleFieldRequestDto): SampleFieldResponseDto {
        val sample = sampleRepository.findByIdAndDeletedFalse(sampleId)
            ?: throw ObjectIdNotFoundException(sampleId)

        val field = dto.toEntity(sample)
        return sampleFieldRepository.save(field).toDTO()
    }

    @Transactional
    override fun updateField(fieldId: Long, dto: SampleFieldRequestDto): SampleFieldResponseDto {
        val field = sampleFieldRepository.findByIdAndDeletedFalse(fieldId)
            ?: throw ObjectIdNotFoundException(fieldId)

        val sample = sampleRepository.findByIdAndDeletedFalse(dto.sampleId)
            ?: throw ObjectIdNotFoundException(dto.sampleId)

        field.apply {
            keyName = dto.keyName
            fieldType = dto.fieldType
            isRequired = dto.isRequired
            this.sample = sample
        }

        return sampleFieldRepository.save(field).toDTO()
    }

    @Transactional
    override fun updateFields(sampleId: Long, sampleFields: List<SampleFieldRequestDto>): SampleResponseDto {
        val sample = sampleRepository.findByIdAndDeletedFalse(sampleId)
            ?: throw ObjectIdNotFoundException(sampleId)


        val ids = sampleFields.mapNotNull { it.id }

        val existingFields = sampleFieldRepository.findAllByIdInAndDeletedFalse(ids)

        if (existingFields.size != ids.size) {
            val foundIds = existingFields.map { it.id!! }.toSet()
            val missingIds = ids.filterNot { foundIds.contains(it) }
            throw ObjectIdsNotFoundException(missingIds)
        }
        val updatedFields = sampleFields.map { dto ->
            val entity = if (dto.id != null) {
                val existing = existingFields.first { it.id == dto.id }
                updateFieldProps(existing, dto.toEntity(sample))
            } else {
                dto.toEntity(sample)
            }
            entity
        }
        sampleFieldRepository.saveAll(updatedFields)
        return sample.toDTO()
    }

    private fun updateFieldProps(old: SampleField, new: SampleField): SampleField = old.apply {
        if (keyName != new.keyName) keyName = new.keyName
        if (fieldType != new.fieldType) fieldType = new.fieldType
        if (fieldReplaceType != new.fieldReplaceType) fieldReplaceType = new.fieldReplaceType
        if (isRequired != new.isRequired) isRequired = new.isRequired
    }


    @Transactional
    override fun deleteField(fieldId: Long) {
        sampleFieldRepository.trash(fieldId)
            ?: throw AlreadyDeletedException(fieldId)
    }

    @Transactional(readOnly = true)
    override fun getFieldsBySampleId(sampleId: Long): List<SampleFieldResponseDto> {
        sampleRepository.findByIdAndDeletedFalse(sampleId)
            ?: throw ObjectIdNotFoundException(sampleId)

        return sampleFieldRepository.findAllBySampleIdAndDeletedFalse(sampleId).map { it.toDTO() }
    }

    override fun updateSampleFile(
        file: MultipartFile?,
        sampleId: Long,
        name: String?,
        allowContractCreation: Boolean?
    ): SampleResponseDto =
        sampleRepository.findByIdAndDeletedFalse(sampleId)
            ?.also { sample ->
                val user = userRepository.findByUsernameAndDeletedFalse(getUserName())
                    .orElseThrow { AccessDeniedException() }

                val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

                when (role) {
                    Role.OPERATOR -> if (sample.organization.id != user.organization?.id
                    ) throw AccessDeniedException()

                    Role.DIRECTOR -> if (sample.organization.id != user.organization?.id) throw AccessDeniedException()
                    else -> throw AccessDeniedException()
                }
            }
            ?.run {
                this.takeIf {
                    name != null && it.name != name
                }?.let {
                    sampleRepository.findByNameAndDeletedFalse(name!!)
                        ?.let { throw SampleNameAlreadyExistsException(name) }
                    it.name = name
                }
                allowContractCreation?.let { this.allowContractCreation = allowContractCreation }
                file?.let { return@run updateSampleFile(it, it.name, this) }

                sampleRepository.save(this).toDTO()
            }
            ?: throw ObjectIdNotFoundException(sampleId)


    @Transactional
    override fun uploadSampleFile(file: MultipartFile, name: String): SampleResponseDto =
        file.originalFilename
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ext ->
                val userName = getUserName()
                val user = getOwner(userName)
                val extractor = extractors.find { it.supports(ext) }
                    ?: throw FileFormatNotFoundException(ext)

                val templateHash = fileUtils.calculateSHA256(file.bytes)
                val text = extractor.extractText(file)

                sampleRepository.findByNameAndDeletedFalse(name)?.let {
                    throw SampleNameAlreadyExistsException(name)
                }

                val sample = Sample(
                    name = name,
                    templateHash = "",
                    filePath = "",
                    owner = user,
                    organization = user.organization!!,
                    allowContractCreation = true
                ).let(sampleRepository::save)

                val fields = extractorFieldExecutor.parseFieldsFromText(text, sample)

                val filePath = sampleRepository.findByTemplateHashAndDeletedFalse(templateHash)
                    ?.filePath
                    ?: fileUtils.saveFile(file, name, ext)

                sample.templateHash = templateHash
                sample.filePath = filePath
                val savedSample = sampleRepository.save(sample)

                sampleFieldRepository.saveAll(fields)

                return savedSample.toDTO()
            }
            ?: throw FileFormatNotFoundException(file.originalFilename)

    override fun getSampleFile(id: Long): ResponseEntity<ByteArray> =
        sampleRepository.findByIdAndDeletedFalse(id)
            ?.filePath
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.extension.isNotBlank() }
            ?.let { file ->
                val mediaType = getMediaType(file.extension)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.name}\"")
                    .contentType(mediaType)
                    .body(file.readBytes())
            }
            ?: throw ObjectIdNotFoundException(id)


    private fun getOwner(userName: String): User =
        userRepository.findByUsernameAndDeletedFalse(userName)
            .getOrNull() ?: throw UserNameFoundException(userName)

    private fun createSpecification(
        user: User,
        role: Role,
        organizationId: Long?,
        search: String?,
        orderDirection: OrderDirection?
    ): Specification<Sample> {
        return Specification { from, query, builder ->
            val predicates = mutableListOf<Predicate>()

            extractRoleBasedPredicates(
                role,
                organizationId,
                predicates,
                builder,
                from,
                user,
                query,
                SamplePermission::class.java,
                "sample",
                search
            )

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)
            query?.distinct(true)

            builder.and(*predicates.toTypedArray())
        }
    }

    private fun updateSampleFile(file: MultipartFile, name: String, sample: Sample): SampleResponseDto =
        file.originalFilename
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ext ->
                val extractor = extractors.find { it.supports(ext) }
                    ?: throw FileFormatNotFoundException(ext)

                val templateHash = fileUtils.calculateSHA256(file.bytes)
                val text = extractor.extractText(file)

                sample.takeIf {
                    it.name != name
                }?.let {
                    sampleRepository.findByNameAndDeletedFalse(name)?.let {
                        throw SampleNameAlreadyExistsException(name)
                    }
                }

                extractorFieldExecutor.parseFieldsFromText(text, sample)

                val filePath = sampleRepository.findByTemplateHashAndDeletedFalse(templateHash)
                    ?.filePath
                    ?: fileUtils.saveFile(file, name, ext)

                sample.templateHash = templateHash
                sample.filePath = filePath
                sample.name = name

                val savedSample = sampleRepository.save(sample)

                return savedSample.toDTO()
            }
            ?: throw FileFormatNotFoundException(file.originalFilename)


}

@Service
class DocumentationServiceImpl(
    private val documentationRepository: DocumentationRepository,
    private val organizationRepository: OrganizationRepository,
    private val documentationValueRepository: DocumentationValueRepository,
    private val sampleRepository: SampleRepository,
    private val sampleFieldRepository: SampleFieldRepository,
    private val userRepository: UserRepository,
    private val userContextService: UserContextService,
    private val fileUtils: FileUtils,
    private val formatConverters: List<FormatConverter>,
    private val factory: ReplaceFileFactory,
    private val exporters: List<Exporter>
) : DocumentationService {

    override fun downloadFile(documentationId: Long, format: String): ByteArray {
        val documentation = documentationRepository.findByIdAndDeletedFalse(documentationId)
            ?: throw ObjectIdNotFoundException(documentationId)

        val replacements = documentation.sample.fields.mapNotNull { field ->
            documentation.values.find { it.field.id == field.id }
                ?.let { value -> field.keyName to (value.field.fieldReplaceType to value.value) }
        }.toMap()

        val originalFileBytes = fileUtils.getFile(documentation.sample.filePath)
            .takeIf { it.exists() }
            ?.readBytes()
            ?: throw FileNotFoundException("File not found: ${documentation.sample.filePath}")

        val extension = documentation.sample.filePath
            .substringAfterLast('.', "")
            .lowercase()

        val docxBytes = if (extension == "docx") {
            originalFileBytes
        } else {
            formatConverters.find { it.supports(extension) }
                ?.toFormat(originalFileBytes)
                ?: throw FileFormatNotFoundException(extension)
        }

        val replacedDocx = factory.replaceFile(ByteArrayInputStream(docxBytes), replacements)

        val exporter = exporters.find { it.supports(format.lowercase()) }
            ?: throw FileFormatNotFoundException(format)

        return exporter.export(replacedDocx, replacements)
    }

    @Transactional
    override fun create(dto: DocumentationRequestDto): DocumentationResponseDto =
        getUserName()
            .let { username ->
                userRepository.findByUsernameAndDeletedFalse(username)
                    .orElseThrow { uz.shukrullaev.com.sample.UsernameNotFoundException(username) }
            }
            .let { user ->

                sampleRepository.existsByIdAndDeletedTrue(dto.sampleId)
                    .runIfTrue { throw SampleDeletedException(dto.sampleId) }


                val sample = sampleRepository.findByIdAndDeletedFalse(dto.sampleId)
                    ?: throw ObjectIdNotFoundException(dto.sampleId)
                sample.allowContractCreation!!.runIfFalse { throw AllowContractCreationException() }

                documentationRepository.existsByNameAndDeletedFalse(dto.name)
                    .runIfTrue { throw DocumentNameAlreadyExistsException(dto.name) }


                checkValues(sample, dto.values)


                val orgId = userContextService.getCurrentOrganizationIdForChange(dto.organizationId)
                val organization = orgId?.let {
                    organizationRepository.findByIdAndDeletedFalse(it)
                        ?: throw ObjectIdNotFoundException(dto.organizationId)
                } ?: user.organization ?: throw OrganizationIdIsNullException()


                dto.toEntity(sample, user, organization)
                    .apply { this.organization = organization }
                    .let(documentationRepository::save)
                    .also { doc ->
                        sampleFieldRepository.findAllBySampleIdAndDeletedFalse(sample.id!!)
                            .mapNotNull { field ->
                                dto.values.find { it.fieldId == field.id }
                                    ?.toEntity(field, doc)
                            }
                            .also { values ->
                                documentationValueRepository.saveAll(values)
                                doc.values = values.toMutableSet()
                            }
                    }
                    .toDTO()
            }

    @Transactional
    override fun update(id: Long, dto: DocumentationRequestDto): DocumentationResponseDto =
        documentationRepository.findByIdAndDeletedFalse(id)
            ?.let { documentation ->
                if (dto.name != documentation.name)
                    documentationRepository.existsByName(dto.name)
                        .runIfTrue { throw DocumentNameAlreadyExistsException(dto.name) }
                val sample = sampleRepository.findByIdAndDeletedFalse(dto.sampleId)
                    ?: throw SampleDeletedException(dto.sampleId)
                sampleRepository.existsByIdAndDeletedTrue(dto.sampleId)
                    .runIfTrue { throw SampleDeletedException(dto.sampleId) }
                val user = getUserName()
                    .let { username ->
                        userRepository.findByUsernameAndDeletedFalse(username)
                            .orElseThrow { uz.shukrullaev.com.sample.UsernameNotFoundException(username) }
                    }
                checkValues(sample, dto.values)

                val sampleFields = sampleFieldRepository.findAllBySampleIdAndDeletedFalse(sample.id!!)
                if (sampleFields.isEmpty()) throw ObjectIdNotFoundException(sample.id)
                val orgId = userContextService.getCurrentOrganizationIdForChange(dto.organizationId)
                val organization = orgId?.let {
                    organizationRepository.findByIdAndDeletedFalse(it)
                        ?: throw ObjectIdNotFoundException(dto.organizationId)
                } ?: user.organization ?: throw OrganizationIdIsNullException()


                val existingValues =
                    documentationValueRepository.findAllByDocumentationIdAndDeletedFalse(documentation.id!!)
                val existingValueMap = existingValues.associateBy { it.field.id }

                dto.values.forEach { dtoValue ->
                    val field = sampleFields.firstOrNull { it.id == dtoValue.fieldId }
                        ?: throw ObjectIdNotFoundException(dtoValue.fieldId)

                    val existing = existingValueMap[dtoValue.fieldId]
                    if (existing != null) {
                        existing.value = dtoValue.value
                    } else {
                        val newValue = dtoValue.toEntity(field, documentation)
                        documentation.values.add(newValue)
                    }
                }

                documentation.name = dto.name
                documentation.sample = sample
                documentation.organization = organization
                documentationRepository.save(documentation)
                documentationValueRepository.saveAll(documentation.values)

                return documentation.toDTO()
            }
            ?: throw ObjectIdNotFoundException(id)


    @Transactional(readOnly = true)
    override fun get(id: Long): DocumentationResponseDto {
        val documentation = documentationRepository.findByIdAndDeletedFalse(id)
            ?: throw ObjectIdNotFoundException(id)
        return documentation.toDTO()
    }

    @Transactional
    override fun delete(id: Long) {
        documentationRepository.trash(id)
            ?: throw ObjectIdNotFoundException(id)
    }

    @Transactional(readOnly = true)
    override fun getAll(
        name: String?,
        organizationId: Long?,
        orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DocumentationResponseDto> {
        val user = getOwner(getUserName())
        val role = getUserRoles().first()
        val orgId = userContextService.getCurrentOrganizationId(organizationId)

        val spec = createSpecification(
            user = user,
            role = role,
            organizationId = orgId,
            search = name,
            orderDirection = orderDirection
        )

        return documentationRepository.findAll(spec, pageable).map { it.toDTO(user) }
    }


    @Transactional(readOnly = true)
    override fun getBySampleId(sampleId: Long, pageable: Pageable): Page<DocumentationResponseDto> {
        return documentationRepository.findAllBySampleIdAndDeletedFalse(sampleId, pageable)
            .map { it.toDTO() }
    }

    @Transactional
    override fun addValue(documentationId: Long, fieldId: Long, value: String): DocumentationValueResponseDto {
        val documentation = documentationRepository.findByIdAndDeletedFalse(documentationId)
            ?: throw ObjectIdNotFoundException(documentationId)

        val field = sampleFieldRepository.findByIdAndDeletedFalse(fieldId)
            ?: throw ObjectIdNotFoundException(fieldId)

        val entity = DocumentationValue(value = value, documentation = documentation, field = field)
        return documentationValueRepository.save(entity).toDTO()
    }

    @Transactional
    override fun updateValue(valueId: Long, newValue: String): DocumentationValueResponseDto {
        val value = documentationValueRepository.findByIdAndDeletedFalse(valueId)
            ?: throw ObjectIdNotFoundException(valueId)

        value.value = newValue
        return documentationValueRepository.save(value).toDTO()
    }

    @Transactional(readOnly = true)
    override fun getValuesByDocumentationId(documentationId: Long): List<DocumentationValueResponseDto> {
        return documentationValueRepository.findAllByDocumentationIdAndDeletedFalse(documentationId)
            .map { it.toDTO() }
    }

    @Transactional
    override fun deleteValue(valueId: Long) {
        documentationValueRepository.trash(valueId) ?: throw AlreadyDeletedException(valueId)
    }

    @Transactional(readOnly = true)
    override fun getAuditInfo(documentationId: Long): AuditInfoDto {
        val doc = documentationRepository.findByIdAndDeletedFalse(documentationId)
            ?: throw ObjectIdNotFoundException(documentationId)

        return AuditInfoDto(
            createdBy = doc.createdBy,
            createdDate = doc.createdDate,
            updatedBy = doc.updatedBy,
            updatedDate = doc.modifiedDate,
        )
    }

    private fun getOwner(userName: String): User =
        userRepository.findByUsernameAndDeletedFalse(userName)
            .getOrNull() ?: throw UserNameFoundException(userName)

    private fun createSpecification(
        user: User,
        role: Role,
        organizationId: Long?,
        search: String?,
        orderDirection: OrderDirection?
    ): Specification<Documentation> {
        return Specification { from, query, builder ->
            val predicates = mutableListOf<Predicate>()

            extractRoleBasedPredicates(
                role,
                organizationId,
                predicates,
                builder,
                from,
                user,
                query,
                DocumentationPermission::class.java,
                "documentation",
                search
            )

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)
            query?.distinct(true)

            builder.and(*predicates.toTypedArray())
        }
    }


    private fun checkValues(sample: Sample, values: Collection<DocumentationValueRequestDto>) {
        values.firstOrNull { value ->
            sample.fields.none { field -> field.id == value.fieldId }
        }?.let { value ->
            throw SampleConflictException(
                sample.id,
                value.value,
                value.fieldId
            )
        } ?: sample.fields.firstOrNull { field ->
            field.isRequired && values.none { value -> value.fieldId == field.id && value.value != null }
        }?.let { missingField ->
            throw SampleValueIsRequiredException(sample.id, null, missingField.id)
        }

    }
}

@Service
class DownloadInfoServiceImpl(
    private val downloadInfoRepository: DownloadInfoRepository,
    private val documentationRepository: DocumentationRepository,
    private val userRepository: UserRepository,
    private val downloadWorker: DownloadWorker
) : DownloadInfoService {

    override fun getAll(
        status: FileDownloadStatus?,
        orderDirection: OrderDirection?,
        pageable: Pageable
    ): Page<DownloadInfoResponseDto> {
        val user = getOwner(getUserName())
        val spec = createSpecification(user.id!!, status, orderDirection)
        return downloadInfoRepository.findAll(spec, pageable).map { it.toDTO() }
    }

    override fun getDocumentsByDownloadInfoId(downloadInfoId: Long): List<DocumentationResponseDto> {
        val info = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
            ?: throw ObjectIdNotFoundException(downloadInfoId)

        return documentationRepository.findAllByDownloadInfoIdAndDeletedFalse(info.id!!)
            .map { it.toDTO() }
    }

    override fun downloadFilesByDownloadInfoId(downloadInfoId: Long): ByteArray {
        val info = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
            ?: throw ObjectIdNotFoundException(downloadInfoId)

        if (info.status != FileDownloadStatus.DOWNLOADED) throw FileNotReadyException(info.status)

        val file = File(info.path ?: throw PathNullException())
        if (!file.exists()) throw FileNotFoundException()

        return file.readBytes()
    }


    @Transactional
    override fun createDownloadInfo(request: CreateDownloadInfoRequest): DownloadInfoResponseDto {
        val user = userRepository.findByUsernameAndDeletedFalse(getUserName())
            .orElseThrow { UsernameNotFoundException(getUserName()) }

        if (request.documentationIds.isEmpty()) throw ObjectIdNotFoundException()

        val documents = documentationRepository.findAllById(request.documentationIds).filterNot { it.deleted }
        if (documents.size != request.documentationIds.size) {
            val foundIds = documents.map { it.id!! }.toSet()
            val notFoundIds = request.documentationIds.filterNot { foundIds.contains(it) }
            throw ObjectIdsNotFoundException(notFoundIds)
        }

        val info = DownloadInfo(
            format = request.format,
            status = FileDownloadStatus.CREATED,
            path = null,
            owner = user
        ).apply {
            documentations.addAll(documents)
        }

        val savedInfo = downloadInfoRepository.save(info)


        val foundedDocuments = documentationRepository.findAllByIdInAndDeletedFalse(request.documentationIds)
        downloadWorker.createZipAsync(savedInfo.id!!, foundedDocuments)


        return savedInfo.toDTO()
    }

    override fun deleteById(infoId: Long) {
        downloadInfoRepository.findByIdAndDeletedFalse(infoId)
            ?.let { downloadInfoRepository.deleteById(infoId) }
            ?: throw ObjectIdNotFoundException(infoId)
    }


    private fun createSpecification(
        ownerId: Long,
        status: FileDownloadStatus?,
        orderDirection: OrderDirection?
    ): Specification<DownloadInfo> {
        return Specification { from, query, builder ->
            val predicates = mutableListOf<Predicate>()

            predicates += builder.equal(from.get<Boolean>("deleted"), false)
            predicates += builder.equal(from.get<User>("owner").get<Long>("id"), ownerId)

            status?.let {
                predicates += builder.equal(from.get<FileDownloadStatus>("status"), it)
            }

            query?.distinct(true)

            val order = when (orderDirection) {
                OrderDirection.ASC -> builder.asc(from.get<Long>("createdDate"))
                else -> builder.desc(from.get<Long>("createdDate"))
            }
            query?.orderBy(order)

            builder.and(*predicates.toTypedArray())
        }
    }

    private fun getOwner(userName: String): User =
        userRepository.findByUsernameAndDeletedFalse(userName)
            .getOrNull() ?: throw UserNameFoundException(userName)

}

@Service("documentationPermissionService")
class DocumentationPermissionServiceImpl(
    private val documentationRepository: DocumentationRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: DocumentationPermissionRepository
) : AbstractPermissionService<
        Documentation, // target
        DocumentationPermission, // permission entity like DOC, SAMPLE
        DocumentationPermissionRequestDto, // request DTO
        DocumentationPermissionResponseDto // response DTO
        >(
    permissionRepository = permissionRepository,

    targetFetcher = { ids ->
        documentationRepository.findAllByIdInAndDeletedFalse(ids)
            .associateBy { it.id!! }
    },

    userFetcher = { ids ->
        userRepository.findAllByIdInAndDeletedFalse(ids)
            .associateBy { it.id!! }
    },

    toEntity = { doc, user, perm ->
        DocumentationPermission(doc, user, perm)
    },

    toDto = { it.toDTO() }
) {
    override fun hasPermission(userId: Long, targetId: Long, permission: Permissions): Boolean =
        permissionRepository.existsByUserIdAndDocumentationIdAndPermissionIn(
            userId, targetId, listOf(permission, Permissions.CRUD)
        ) || documentationRepository.existsByIdAndOwnerIdAndDeletedFalse(targetId, getUserId())
}

@Service("samplePermissionService")
class SamplePermissionServiceImpl(
    private val sampleRepository: SampleRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: SamplePermissionRepository
) : AbstractPermissionService<
        Sample,
        SamplePermission,
        SamplePermissionRequestDto,
        SamplePermissionResponseDto
        >(
    permissionRepository = permissionRepository,

    targetFetcher = { ids ->
        sampleRepository.findAllByIdInAndDeletedFalse(ids)
            .associateBy { it.id!! }
    },

    userFetcher = { ids ->
        userRepository.findAllByIdInAndDeletedFalse(ids)
            .associateBy { it.id!! }
    },

    toEntity = { sample, user, permission ->
        SamplePermission(sample, user, permission)
    },

    toDto = { it.toDTO() }
) {
    override fun hasPermission(userId: Long, targetId: Long, permission: Permissions): Boolean =
        permissionRepository.existsByUserIdAndSampleIdAndPermissionIn(
            userId, targetId, listOf(permission, Permissions.CRUD)
        ) || sampleRepository.existsByIdAndOwnerIdAndDeletedFalse(targetId, getUserId())
}

private fun <T : BaseEntity, P : AbstractPermission<T>> extractRoleBasedPredicates(
    role: Role,
    organizationId: Long?,
    predicates: MutableList<Predicate>,
    builder: CriteriaBuilder,
    from: From<*, T>,
    user: User,
    query: CriteriaQuery<*>?,
    permissionClass: Class<P>,
    targetFieldName: String,
    search: String?
) {
    predicates += builder.equal(from.get<Boolean>("deleted"), false)

    search?.let {
        val name = from.get<String>("name")
        predicates += builder.like(builder.lower(name), "%${it.trim().lowercase()}%")
    }
    when (role) {
        Role.ADMIN, Role.DIRECTOR -> {
            organizationId?.let {
                predicates += builder.equal(
                    from.get<Organization>("organization").get<Long>("id"),
                    it
                )
            }
        }

        Role.OPERATOR -> {
            val ownerPredicate = builder.equal(from.get<User>("owner").get<Long>("id"), user.id)

            val subQuery = query!!.subquery(Long::class.java)
            val permissionRoot = subQuery.from(permissionClass)

            subQuery.select(permissionRoot.get<T>(targetFieldName).get("id"))
                .where(
                    builder.equal(permissionRoot.get<User>("user").get<Long>("id"), user.id),
                    builder.equal(permissionRoot.get<Boolean>("deleted"), false)
                )

            val permissionPredicate = from.get<Long>("id").`in`(subQuery)
            predicates += builder.or(ownerPredicate, permissionPredicate)
        }
    }
}
