package uz.shukrullaev.com.sample

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdullah
 * @since 11/07/2025 4:45 pm
 */

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun existsByIdAndDeletedFalse(id: Long): Boolean?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): Page<T>
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>, entityManager: EntityManager,
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }
    override fun existsByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { !deleted }

    @Transactional
    override fun trash(id: Long): T? =
        findByIdOrNull(id)?.takeIf { !it.deleted }?.run {
            deleted = true
            save(this)
        }


    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): Page<T> = findAll(isNotDeletedSpecification, pageable)
    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

}

@Repository
interface OrganizationRepository : BaseRepository<Organization> {
    fun existsByIdentifierNumberAndDeletedFalse(identifierNumber: String): Boolean

}

@Repository
interface UserRepository : BaseRepository<User> {

    fun findAllByIdInAndDeletedFalse(ids: Set<Long>): List<User>

    fun findByUsername(username: String): Optional<User>
    fun findByUsernameAndDeletedFalse(username: String): Optional<User>
}

@Repository
interface SampleRepository : BaseRepository<Sample> {
    fun existsByOwnerIdAndDeletedFalse(ownerId: Long): Boolean
    fun findByNameAndDeletedFalse(name: String): Sample?

    fun findAllByIdInAndDeletedFalse(ids: Set<Long>): List<Sample>

    fun existsByIdAndDeletedTrue(id: Long): Boolean

    @Query(value = "select * from sample where template_hash = :templateHash limit 1", nativeQuery = true)
    fun findByTemplateHashAndDeletedFalse(templateHash: String): Sample?
}

@Repository
interface SampleFieldRepository : BaseRepository<SampleField> {
    fun findAllBySampleIdAndDeletedFalse(sampleId: Long): List<SampleField>
    fun findAllByIdInAndDeletedFalse(sampleFields: List<Long>): List<SampleField>
}

@Repository
interface DocumentationRepository : BaseRepository<Documentation> {

    fun existsByOwnerIdAndDeletedFalse(ownerId: Long): Boolean

    fun findAllByIdInAndDeletedFalse(ids: Set<Long>): List<Documentation>

    @Query("""
    select distinct d from Documentation d
    join fetch d.sample s
    join fetch s.fields f
    join fetch d.values v
    where d.id in :ids and d.deleted = false
""")
    fun findAllByIdInAndDeletedFalse(@Param("ids") ids: List<Long>): List<Documentation>

    @Query(
        """
        SELECT d FROM Documentation d 
        JOIN d.downloadInfos info 
        WHERE d.deleted = false AND info.id = :infoId
        """
    )
    fun findAllByDownloadInfoIdAndDeletedFalse(@Param("infoId") infoId: Long): List<Documentation>

    fun existsByNameAndDeletedFalse(name: String): Boolean
    fun existsByName(name: String): Boolean


    fun findAllBySampleIdAndDeletedFalse(sampleId: Long, pageable: Pageable): Page<Documentation>

}

@Repository
interface DocumentationValueRepository : BaseRepository<DocumentationValue> {
    fun findAllByDocumentationIdAndDeletedFalse(documentationId: Long): List<DocumentationValue>
}

@Repository
interface DownloadInfoRepository : BaseRepository<DownloadInfo>

interface DocumentationPermissionRepository : BaseRepository<DocumentationPermission> {
    fun existsByUserIdAndDocumentationIdAndPermissionIn(
        userId: Long,
        documentationId: Long,
        permissions: Collection<Permissions>
    ): Boolean

}

interface SamplePermissionRepository : BaseRepository<SamplePermission> {


    fun existsByUserIdAndSampleIdAndPermissionIn(
        userId: Long,
        documentationId: Long,
        permissions: Collection<Permissions>
    ): Boolean

}


