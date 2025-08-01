package uz.shukrullaev.com.sample

import com.fasterxml.jackson.databind.ObjectMapper
import com.opencsv.CSVReaderBuilder
import jakarta.annotation.PreDestroy
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.jodconverter.local.office.LocalOfficeManager
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 12/07/2025 11:57 am
 */

val replaceRegex = Regex("""\{\{([A-Za-z0-9_]+)}}|(?<!\w)[A-Z0-9_]{2,}(?!\w)""")

val rightRegex = Regex("""[A-Za-z0-9_-]+(?=\s*[:\-])""")

interface SupportType {
    fun supports(extension: String): Boolean
}

interface TextExtractor : SupportType {
    fun extractText(file: MultipartFile): String
}

interface ReplaceFileFactory : SupportType {
    fun replaceFile(
        template: InputStream, replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray
}

interface Exporter : SupportType {
    fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray
}

interface FormatConverter : SupportType {
    fun toDocx(input: ByteArray): ByteArray
}


@Component
class CsvTextExtractor : TextExtractor {

    override fun supports(extension: String): Boolean =
        extension.equals("csv", ignoreCase = true)

    override fun extractText(file: MultipartFile): String {
        val reader = CSVReaderBuilder(InputStreamReader(file.inputStream)).build()
        val builder = StringBuilder()

        reader.forEach { row ->
            val line = row.joinToString(" ") { it.trim() }
            if (line.isNotBlank()) builder.appendLine(line)
        }

        return builder.toString()
    }
}

@Component
class DocxTextExtractor : TextExtractor {
    override fun supports(extension: String) = extension.equals("docx", true)

    //others build on AI to save time
    override fun extractText(file: MultipartFile): String {
        val doc = XWPFDocument(file.inputStream)
        val textBuilder = StringBuilder()

        doc.paragraphs.forEach { p ->
            val text = p.text.trim()
            if (text.isNotBlank()) textBuilder.appendLine(text)
        }

        doc.tables.forEach { table ->
            table.rows.forEach { row ->
                row.tableCells.forEach { cell ->
                    val text = cell.text.trim()
                    if (text.isNotBlank()) textBuilder.appendLine(text)
                }
            }
        }

        doc.headerList?.forEach { header ->
            header.paragraphs.forEach {
                val text = it.text.trim()
                if (text.isNotBlank()) textBuilder.appendLine(text)
            }
        }

        doc.footerList?.forEach { footer ->
            footer.paragraphs.forEach {
                val text = it.text.trim()
                if (text.isNotBlank()) textBuilder.appendLine(text)
            }
        }

        return textBuilder.toString()
    }
}

@Component
class JsonTextExtractor(
    private val objectMapper: ObjectMapper,
) : TextExtractor {

    override fun supports(extension: String) = extension.equals("json", ignoreCase = true)

    override fun extractText(file: MultipartFile): String {
        val jsonString = file.inputStream.bufferedReader().readText()
        val jsonNode = objectMapper.readTree(jsonString)

        val keysToCheck = listOf("text", "template", "body", "content")

        for (key in keysToCheck) {
            val value = jsonNode[key]?.asText()
            if (!value.isNullOrBlank()) return value
        }

        return jsonString
    }
}


@Component
class TxtTextExtractor : TextExtractor {

    override fun supports(extension: String) = extension.equals("txt", ignoreCase = true)

    override fun extractText(file: MultipartFile): String {
        return file.inputStream.bufferedReader().readText()
    }
}

@Service
class DocxReplaceFactoryImpl : ReplaceFileFactory {


    override fun supports(extension: String) = extension.equals("docx", true)

    override fun replaceFile(
        template: InputStream,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray {
        val doc = XWPFDocument(template)
        doc.paragraphs.forEach { paragraph ->
            replaceRuns(paragraph.runs, replacements)
        }

        doc.tables.forEach { table ->
            table.rows.forEach { row ->
                row.tableCells.forEach { cell ->
                    cell.paragraphs.forEach { paragraph ->
                        replaceRuns(paragraph.runs, replacements)
                    }
                }
            }
        }

        val outputStream = ByteArrayOutputStream()
        doc.write(outputStream)
        return outputStream.toByteArray()
    }


    private fun replaceRuns(
        runs: List<XWPFRun>,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ) {
        for (i in runs.indices) {
            val run = runs[i]
            val text = run.getText(0) ?: continue

            var replaced = text

            replaceRegex.findAll(text).forEach { match ->
                val rawKey = match.groupValues[1].ifBlank { match.value }
                val (type, value) = replacements[rawKey] ?: return@forEach
                if (type == FieldReplaceType.REPLACE) {
                    replaced = replaced.replace(match.value, value)
                }
            }

            val nextRunText = runs.getOrNull(i + 1)?.getText(0)?.trim()
            val (type, value) = replacements[text] ?: (null to null)
            if (type == FieldReplaceType.RIGHT && nextRunText == ":") {
                replaced = "$text: $value"
                runs[i + 1].setText("", 0)
            }

            run.setText(replaced, 0)
        }
    }

}

@Service
class CsvExporter : Exporter {

    override fun supports(extension: String) = extension.equals("csv", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

        val headers = replacements.keys.joinToString(",") { "\"$it\"" }
        val values = replacements.values.joinToString(",") { (_, value) -> "\"$value\"" }

        writer.write(headers + "\n")
        writer.write(values + "\n")
        writer.flush()

        return outputStream.toByteArray()
    }
}

@Service
class DocxExporter : Exporter {
    override fun supports(extension: String) = extension.equals("docx", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray = fileByteArray
}

@Service
class PdfExporter(
    private val pdfConverter: PdfConverter,
) : Exporter {

    override fun supports(extension: String) = extension.equals("pdf", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray {
        return pdfConverter.convertDocxToPdf(fileByteArray)
    }
}


@Service
class TxtExporter : Exporter {

    override fun supports(extension: String) = extension.equals("txt", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String>>,
    ): ByteArray {
        val content = replacements.entries.joinToString("\n") { (key, value) ->
            "$key: ${value.second}"
        }
        return content.toByteArray(Charsets.UTF_8)
    }
}

@Component
class ExtractorFieldExecutor {

    fun parseFieldsFromText(text: String, sample: Sample): List<SampleField> {
        if (text.isBlank()) throw FileFormatNotFoundException(text)

        val replaceFields = replaceRegex.findAll(text)
            .map { it.groups[1]?.value ?: it.value }
            .map { it.trim() }
            .filter { it.length < 64 }
            .filterNot { it.all { c -> c == '-' } }
            .filterNot { it.count { c -> c == '-' } > 10 }
            .toSet()

        val rightFields = rightRegex.findAll(text)
            .map { it.value.trim() }
            .filter { it.length < 64 }
            .filterNot { it.all { c -> c == '-' } }
            .filterNot { it.count { c -> c == '-' } > 10 }
            .toSet()

        val allFields = mutableListOf<SampleField>()

        replaceFields.forEach { field ->
            allFields += SampleField(
                keyName = field,
                fieldType = FieldType.STRING,
                fieldReplaceType = FieldReplaceType.REPLACE,
                isRequired = false,
                sample = sample
            )
        }

        rightFields.forEach { field ->
            if (replaceFields.contains(field)) return@forEach
            allFields += SampleField(
                keyName = field,
                fieldType = FieldType.STRING,
                fieldReplaceType = FieldReplaceType.RIGHT,
                isRequired = false,
                sample = sample
            )
        }

        return allFields
    }
}

@Component
class FileUtils {
    fun getFile(filePath: String): File {
        return File(filePath)
    }

    fun calculateSHA256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun saveFile(file: MultipartFile, fileName: String, format: String): String {
        val baseDir = "uploads/samples"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9-_]"), "_")
        val finalFileName = "${safeFileName}_$timestamp.$format"
        val fullPath = Paths.get(baseDir, finalFileName)

        Files.createDirectories(fullPath.parent)

        Files.write(fullPath, file.bytes, StandardOpenOption.CREATE_NEW)

        return fullPath.toString()
    }
}


@Component
class PdfConverter {

    private val officeManager: OfficeManager = LocalOfficeManager.builder()
        .portNumbers(2004)
        .officeHome(File("C:/Program Files/LibreOffice"))
        .install().build()

    init {
        officeManager.start()
    }

    fun convertDocxToPdf(docxBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(docxBytes)
        val outputStream = ByteArrayOutputStream()

        LocalConverter.builder()
            .officeManager(officeManager)
            .build()
            .convert(inputStream)
            .to(outputStream)
            .`as`(DefaultDocumentFormatRegistry.PDF)
            .execute()

        return outputStream.toByteArray()
    }


    @PreDestroy
    fun stopOffice() {
        officeManager.stop()
    }
}

@Service
class PdfToDocxConverter : FormatConverter {

    private val officeManager: OfficeManager = LocalOfficeManager.builder()
        .portNumbers(2003)
        .officeHome(File("C:/Program Files/LibreOffice"))
        .install().build()

    init {
        officeManager.start()
    }

    override fun supports(extension: String) = extension.equals("pdf", true)

    override fun toDocx(input: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(input)
        val outputStream = ByteArrayOutputStream()

        LocalConverter.builder()
            .officeManager(officeManager)
            .build()
            .convert(inputStream)
            .to(outputStream)
            .`as`(DefaultDocumentFormatRegistry.DOCX)
            .execute()

        return outputStream.toByteArray()
    }

    @PreDestroy
    fun stopOffice() {
        officeManager.stop()
    }
}

@Service
class CsvToDocxConverter : FormatConverter {

    override fun supports(extension: String) = extension.equals("csv", ignoreCase = true)

    override fun toDocx(input: ByteArray): ByteArray {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(input)))
        val doc = XWPFDocument()

        val table = doc.createTable()

        reader.lineSequence().forEachIndexed { rowIndex, line ->
            val cells = line.split(",")
            val row = if (rowIndex == 0) table.getRow(0) else table.createRow()
            cells.forEachIndexed { cellIndex, cellValue ->
                val cell = if (rowIndex == 0 && cellIndex == 0) row.getCell(0)
                else row.getCell(cellIndex) ?: row.createCell()
                cell.text = cellValue.trim()
            }
        }

        val out = ByteArrayOutputStream()
        doc.write(out)
        return out.toByteArray()
    }
}

@Service
class TxtToDocxConverter : FormatConverter {

    override fun supports(extension: String) = extension.equals("txt", ignoreCase = true)

    override fun toDocx(input: ByteArray): ByteArray {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(input)))
        val doc = XWPFDocument()

        reader.lineSequence().forEach { line ->
            val paragraph = doc.createParagraph()
            val run = paragraph.createRun()
            run.setText(line)
        }

        val out = ByteArrayOutputStream()
        doc.write(out)
        return out.toByteArray()
    }
}

@Service
class UserContextService(
    private val userRepository: UserRepository
) {

    fun getCurrentOrganizationId(requestedOrgId: Long?): Long {
        val username = getUserName()
        val user = userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow { UsernameNotFoundException(username) }

        val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

        return when {
            role == Role.ADMIN && requestedOrgId == null ->
                throw OrganizationIdIsNullException()

            role == Role.ADMIN && requestedOrgId != null ->
                requestedOrgId

            role == Role.DIRECTOR || role == Role.OPERATOR ->
                user.organization?.id ?: throw OrganizationIdIsNullException()

            else -> throw AccessDeniedException()
        }
    }
}

@Service
class SseEmitterService(
    private val downloadInfoRepository: DownloadInfoRepository
) {

    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    fun register(): SseEmitter {
        val emitter = SseEmitter(60_000L)
        emitters += emitter

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }

        return emitter
    }

    fun emitToAll(dto: DownloadInfoResponseDto) {
        val deadEmitters = mutableListOf<SseEmitter>()

        emitters.forEach { emitter ->
            try {
                emitter.send(dto)
                if (dto.status == FileDownloadStatus.DOWNLOADED || dto.status == FileDownloadStatus.ERROR) {
                    emitter.complete()
                    deadEmitters += emitter
                }
            } catch (ex: Exception) {
                emitter.completeWithError(ex)
                deadEmitters += emitter
            }
        }

        emitters.removeAll(deadEmitters)
    }
}

@Service
class DownloadWorker(
    private val downloadInfoRepository: DownloadInfoRepository,
    private val fileUtils: FileUtils,
    private val factory: ReplaceFileFactory,
    private val formatConverters: List<FormatConverter>,
    private val exporters: List<Exporter>,
    private val sseEmitterService: SseEmitterService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createZipAsync(downloadInfoId: Long, documents: List<Documentation>): CompletableFuture<Void> {
        val info = waitForDownloadInfo(downloadInfoId)
            ?: return CompletableFuture.completedFuture(null)

        try {
            info.status = FileDownloadStatus.DOWNLOADING
            downloadInfoRepository.save(info)
            sseEmitterService.emitToAll(info.toDTO())

            val baseDir = Paths.get("uploads", "downloads")
            Files.createDirectories(baseDir)

            val zipFileName = "documents_${UUID.randomUUID()}.zip"
            val zipPath = baseDir.resolve(zipFileName)

            FileOutputStream(zipPath.toFile()).use { fileOut ->
                ZipOutputStream(fileOut).use { zipStream ->
                    documents.forEach { doc ->
                        info.status = FileDownloadStatus.PROGRESS
                        downloadInfoRepository.save(info)
                        sseEmitterService.emitToAll(info.toDTO())
                        val replacements = doc.sample.fields.mapNotNull { field ->
                            doc.values.find { it.field.id == field.id }
                                ?.let { value -> field.keyName to (value.field.fieldReplaceType to value.value) }
                        }.toMap()

                        val originalFile = fileUtils.getFile(doc.sample.filePath)
                        if (!originalFile.exists()) return@forEach

                        val originalBytes = originalFile.readBytes()

                        val ext = doc.sample.filePath.substringAfterLast('.', "").lowercase()
                        val docxBytes = if (ext == "docx") {
                            originalBytes
                        } else {
                            formatConverters.find { it.supports(ext) }
                                ?.toDocx(originalBytes) ?: originalBytes
                        }

                        val replacedDocx = factory.replaceFile(ByteArrayInputStream(docxBytes), replacements)
                        val exporter = exporters.find { it.supports(info.format.lowercase()) } ?: return@forEach
                        val exportedBytes = exporter.export(replacedDocx, replacements)

                        val entry = ZipEntry("${doc.name}.${info.format.lowercase()}")
                        zipStream.putNextEntry(entry)
                        zipStream.write(exportedBytes)
                        zipStream.closeEntry()
                    }
                }
            }

            val updatedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
                ?: return CompletableFuture.completedFuture(null)

            updatedInfo.status = FileDownloadStatus.DOWNLOADED
            sseEmitterService.emitToAll(info.toDTO())
            updatedInfo.path = zipPath.toString()
            downloadInfoRepository.save(updatedInfo)

        } catch (ex: Exception) {
            val failedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
                ?: return CompletableFuture.completedFuture(null)

            failedInfo.status = FileDownloadStatus.ERROR
            failedInfo.path = null
            log.error("❌ Zip Error: ${ex.message}", ex)
            downloadInfoRepository.save(failedInfo)
        }

        return CompletableFuture.completedFuture(null)
    }


    fun waitForDownloadInfo(downloadInfoId: Long, maxRetries: Int = 10, delayMs: Long = 500): DownloadInfo? {
        repeat(maxRetries) {
            val info = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
            if (info != null) return info
            Thread.sleep(delayMs)
        }
        return null
    }
}
