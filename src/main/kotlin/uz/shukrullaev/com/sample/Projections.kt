package uz.shukrullaev.com.sample


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 31/07/2025 3:20 pm
 */

interface DocumentationDownloadProjection {
    fun getName(): String
    fun getSample(): SampleProjection
    fun getValues(): List<DocumentationValueProjection>
}

interface SampleProjection {
    fun getFilePath(): String
    fun getFields(): List<SampleFieldProjection>
}

interface SampleFieldProjection {
    fun getId(): Long
    fun getKeyName(): String
    fun getFieldReplaceType(): FieldReplaceType
}

interface DocumentationValueProjection {
    fun getValue(): String
    fun getField(): FieldIdProjection
}interface DocumentationExportProjection {
    val name: String
    val sampleFilePath: String
    val sampleFields: List<FieldProjection>
    val values: List<ValueProjection>
}

interface FieldProjection {
    val id: Long
    val keyName: String
    val fieldReplaceType: FieldReplaceType
}

interface ValueProjection {
    val fieldId: Long
    val value: String
}


interface FieldIdProjection {
    fun getId(): Long
}
