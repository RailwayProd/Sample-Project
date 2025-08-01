package uz.shukrullaev.com.sample

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:16 pm
 */

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @CreatedDate @Column(updatable = false) var createdDate: Instant? = null,
    @LastModifiedDate var modifiedDate: Instant? = null,
    @Column(nullable = false) var deleted: Boolean = false,
    @CreatedBy @Column(updatable = false) var createdBy: String? = null,
    @LastModifiedBy var updatedBy: String? = null,
)

@MappedSuperclass
abstract class AbstractPermission<T : Any>(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val permission: Permissions
) : BaseEntity() {
    abstract fun getTarget(): T
}


@Entity
@Table(name = "organization")
class Organization(
    @Column(nullable = false) var name: String,
    @Column(nullable = false, length = 14, unique = true) var identifierNumber: String,
) : BaseEntity()

@Entity
@Table(name = "app_user")
class User(
    @Column var fullName: String?,
    @Column(nullable = false, unique = true) var username: String? = null,
    @Column(nullable = false) var password: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: Status? = Status.ACTIVE,
    @ManyToOne var organization: Organization? = null,
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val samplePermissions: MutableSet<SamplePermission> = mutableSetOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val documentationPermission: MutableSet<DocumentationPermission> = mutableSetOf()

) : BaseEntity()

@Entity
@Table(name = "sample")
class Sample(
    @Column(nullable = false, unique = true) var name: String,
    @Column(nullable = false) var filePath: String,
    @Column(nullable = false) var templateHash: String,

    @OneToMany(mappedBy = "sample")
    var fields: MutableSet<SampleField> = mutableSetOf(),

    @ManyToOne var owner: User,
    @ManyToOne var organization: Organization,
) : BaseEntity()

@Entity
@Table(name = "sample_field")
class SampleField(
    @Column(nullable = false) var keyName: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var fieldType: FieldType,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var fieldReplaceType: FieldReplaceType,
    @Column(nullable = false) var isRequired: Boolean = false,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sample_id", nullable = false) var sample: Sample,
    @OneToMany(mappedBy = "field", cascade = [CascadeType.ALL], orphanRemoval = true)
    var values: MutableList<DocumentationValue> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "documentation")
class Documentation(
    @Column(nullable = false, unique = true) var name: String,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sample_id", nullable = false) var sample: Sample,
    @OneToMany(mappedBy = "documentation")
    var values: MutableSet<DocumentationValue> = mutableSetOf(),
    @ManyToOne val owner: User,
    @ManyToOne val organization: Organization,
    @ManyToMany(mappedBy = "documentations") var downloadInfos: MutableSet<DownloadInfo> = mutableSetOf()
) : BaseEntity()

@Entity
@Table(name = "documentation_value")
class DocumentationValue(
    @Column(nullable = false) var value: String,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "field_id", nullable = false) var field: SampleField,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "documentation_id", nullable = false)
    var documentation: Documentation,
) : BaseEntity()

@Entity
@Table(name = "download_info")
class DownloadInfo(
    @Column(nullable = false) var format: String,
    @Enumerated(EnumType.STRING) var status: FileDownloadStatus?,
    @ManyToOne val owner: User,
    @Column(nullable = true) var path: String? = null,
    @ManyToMany @JoinTable(
        name = "download_info_documentation",
        joinColumns = [JoinColumn(name = "download_info_id")],
        inverseJoinColumns = [JoinColumn(name = "documentation_id")]
    )
    var documentations: MutableSet<Documentation> = mutableSetOf(),
) : BaseEntity()

@Entity
@Table(name = "documentation_permissions")
class DocumentationPermission(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documentation_id", nullable = false)
    val documentation: Documentation,

    user: User,
    permission: Permissions
) : AbstractPermission<Documentation>(user, permission) {
    override fun getTarget(): Documentation = documentation
}


@Entity
@Table(name = "sample_permissions")
class SamplePermission(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sample_id", nullable = false)
    val sample: Sample,

    user: User,
    permission: Permissions
) : AbstractPermission<Sample>(user, permission) {
    override fun getTarget(): Sample = sample
}
