package uz.shukrullaev.com.sample

import com.fasterxml.jackson.databind.ObjectMapper
import com.opencsv.CSVReaderBuilder
import jakarta.annotation.PreDestroy
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.jodconverter.core.DocumentConverter
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
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 12/07/2025 11:57 am
 */

val replaceRegex = Regex("""\{\{([^}]*)}}""")

interface SupportType {
    fun supports(extension: String): Boolean
}

interface TextExtractor : SupportType {
    fun extractText(file: MultipartFile): String
}

interface ReplaceFileFactory : SupportType {
    fun replaceFile(
        template: InputStream, replacements: Map<String, Pair<FieldReplaceType, String?>>,
    ): ByteArray
}

interface Exporter : SupportType {
    fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
    ): ByteArray
}

interface FormatConverter : SupportType {
    fun toFormat(input: ByteArray): ByteArray
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
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
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
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
    ) {
        val fullText = runs.joinToString("") { it.getText(0) ?: "" }

        var replacedText = fullText
        replaceRegex.findAll(fullText).forEach { match ->
            val rawKey = match.groupValues[1].trim()
            val (type, value) = replacements[rawKey] ?: return@forEach
            if (type == FieldReplaceType.REPLACE) {
                replacedText = value?.let { replacedText.replace(match.value, it) } ?: ""
            }
        }

        runs.forEach { it.setText("", 0) }
        if (runs.isNotEmpty()) {
            runs[0].setText(replacedText, 0)
        }
    }

//    private fun replaceRunsV1(
//        runs: List<XWPFRun>,
//        replacements: Map<String, Pair<FieldReplaceType, String?>>,
//    ) {
//        for (i in runs.indices) {
//            val run = runs[i]
//            val text = run.getText(0) ?: continue
//
//            var replaced = text
//
//            replaceRegex.findAll(text).forEach { match ->
//                val rawKey = match.groupValues[1].ifBlank { "{{$match.value}}" }
//                val (type, value) = replacements[rawKey] ?: return@forEach
//                if (type == FieldReplaceType.REPLACE) {
//                    replaced = value?.let { replaced.replace(match.value, value) } ?: ""
//                }
//            }
//            run.setText(replaced, 0)
//        }
//    }  bu {{}} ga ishlamidi sababi runlani ajratib chiqanidim FIELD_NAME bosa ishlidi
//    {{FIELD_NAME}} ishlamidi alohida run bop kegani uchun {{ keyingi loop FIELD_NAME keyingi loop }}

}

@Service
class CsvExporter : Exporter {

    override fun supports(extension: String) = extension.equals("csv", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
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
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
    ): ByteArray = fileByteArray
}

@Service
class PdfExporter(
    private val pdfConverter: PdfConverter,
) : Exporter {

    override fun supports(extension: String) = extension.equals("pdf", true)
    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
    ): ByteArray {
        return try {
            pdfConverter.convertDocxToPdf(fileByteArray)
        } catch (e: Exception) {
            println("⚠️ PDF conversion failed: ${e.message}")
            ByteArray(0)
        }
    }
}


@Service
class TxtExporter : Exporter {

    override fun supports(extension: String) = extension.equals("txt", true)

    override fun export(
        fileByteArray: ByteArray,
        replacements: Map<String, Pair<FieldReplaceType, String?>>,
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
            .filterNot { it.count { c -> c == '-' } > 5 }
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

    private val officeManager: OfficeManager?
    private val converter: DocumentConverter?
    private val isAvailable: Boolean

    init {
        var started = false
        var manager: OfficeManager? = null
        var docConverter: DocumentConverter? = null

        try {
            val possiblePaths = listOf(
                File("/usr/lib/libreoffice"),
                File("/opt/libreoffice"),
                File("/usr/local/libreoffice"),
                File("C:/Program Files/LibreOffice")
            )

            val officeHome = possiblePaths.firstOrNull { it.exists() }

            if (officeHome != null) {
                manager = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .portNumbers(2004)
                    .install()
                    .build()
                manager.start()
                started = true

                docConverter = LocalConverter.builder()
                    .officeManager(manager)
                    .build()

                println("✅ LibreOffice (PDF) started from: ${officeHome.absolutePath}")
            } else {
                println("⚠️ LibreOffice not found (PDF converter). Skipping setup.")
            }
        } catch (e: Exception) {
            println("❌ Failed to start LibreOffice (PDF): ${e.message}")
        }

        this.officeManager = manager
        this.converter = docConverter
        this.isAvailable = started
    }

    fun convertDocxToPdf(docxBytes: ByteArray): ByteArray {
        if (!isAvailable || officeManager == null || converter == null) {
            throw IllegalStateException("LibreOffice is not available for PDF conversion.")
        }

        val inputStream = ByteArrayInputStream(docxBytes)
        val outputStream = ByteArrayOutputStream()

        converter.convert(inputStream)
            .`as`(DefaultDocumentFormatRegistry.DOCX)
            .to(outputStream)
            .`as`(DefaultDocumentFormatRegistry.PDF)
            .execute()

        return outputStream.toByteArray()
    }

    @PreDestroy
    fun stopOffice() {
        try {
            officeManager?.stop()
            println("🛑 LibreOffice (PDF) stopped.")
        } catch (e: Exception) {
            println("⚠️ Failed to stop LibreOffice (PDF): ${e.message}")
        }
    }
}


@Component
class PdfToDocxConverter : FormatConverter {

    private val officeManager: OfficeManager?
    private val converter: DocumentConverter?
    private val isAvailable: Boolean

    init {
        var started = false
        var manager: OfficeManager? = null
        var docConverter: DocumentConverter? = null

        try {
            val possiblePaths = listOf(
                File("/usr/lib/libreoffice"),
                File("/opt/libreoffice"),
                File("/usr/local/libreoffice"),
                File("C:/Program Files/LibreOffice")
            )

            val officeHome = possiblePaths.firstOrNull { it.exists() }

            if (officeHome != null) {
                manager = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .portNumbers(2003)
                    .install()
                    .build()
                manager.start()
                started = true

                docConverter = LocalConverter.builder()
                    .officeManager(manager)
                    .build()

                println("✅ LibreOffice started from: ${officeHome.absolutePath}")
            } else {
                println("⚠️ LibreOffice not found. PDF to DOCX conversion will not work.")
            }
        } catch (e: Exception) {
            println("❌ LibreOffice failed to start: ${e.message}")
            // ❌ OLDIN throw qilayotganding — ENDI QILMAYMIZ
            // throw IllegalStateException("LibreOffice manager could not be started: ${e.message}", e)
        }

        this.officeManager = manager
        this.converter = docConverter
        this.isAvailable = started
    }

    override fun supports(extension: String): Boolean {
        return extension.equals("pdf", ignoreCase = true)
    }

    override fun toFormat(input: ByteArray): ByteArray {
        if (!isAvailable || officeManager == null || converter == null) {
            throw IllegalStateException("LibreOffice is not available.")
        }

        val inputStream = ByteArrayInputStream(input)
        val outputStream = ByteArrayOutputStream()

        converter.convert(inputStream)
            .`as`(DefaultDocumentFormatRegistry.PDF)
            .to(outputStream)
            .`as`(DefaultDocumentFormatRegistry.DOCX)
            .execute()

        return outputStream.toByteArray()
    }

    @PreDestroy
    fun stopOffice() {
        try {
            officeManager?.stop()
            println("🛑 LibreOffice stopped.")
        } catch (e: Exception) {
            println("⚠️ Failed to stop LibreOffice: ${e.message}")
        }
    }
}


@Service
class CsvToDocxConverter : FormatConverter {

    override fun supports(extension: String) = extension.equals("csv", ignoreCase = true)

    override fun toFormat(input: ByteArray): ByteArray {
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

    override fun toFormat(input: ByteArray): ByteArray {
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

    fun getCurrentOrganizationId(requestedOrgId: Long?): Long? {
        val username = getUserName()
        val user = userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow { UsernameNotFoundException(username) }

        val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

        return when {
            role == Role.ADMIN && requestedOrgId == null ->
                null

            role == Role.ADMIN && requestedOrgId != null ->
                requestedOrgId

            role == Role.DIRECTOR || role == Role.OPERATOR ->
                user.organization?.id ?: throw OrganizationIdIsNullException()

            else -> throw AccessDeniedException()
        }
    }

    fun getCurrentOrganizationIdForChange(requestedOrgId: Long?): Long? {
        val username = getUserName()
        val user = userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow { UsernameNotFoundException(username) }

        val role = getUserRoles().firstOrNull() ?: throw AccessDeniedException()

        return when {
            role == Role.ADMIN && requestedOrgId == null -> throw AccessDeniedException()
            role == Role.ADMIN && requestedOrgId != null ->
                requestedOrgId

            role == Role.DIRECTOR || role == Role.OPERATOR ->
                user.organization?.id ?: throw OrganizationIdIsNullException()

            else -> throw AccessDeniedException()
        }
    }
}

@Service
class DownloadWorker(
    private val downloadInfoRepository: DownloadInfoRepository,
    private val fileUtils: FileUtils,
    private val factory: ReplaceFileFactory,
    private val formatConverters: List<FormatConverter>,
    private val exporters: List<Exporter>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createZipAsyncParallel(downloadInfoId: Long, documents: List<Documentation>): CompletableFuture<Void> {
        val info = waitForDownloadInfo(downloadInfoId)
            ?: return CompletableFuture.completedFuture(null)

        try {
            info.status = FileDownloadStatus.DOWNLOADING
            downloadInfoRepository.save(info)

            val baseDir = Paths.get("uploads", "downloads")
            Files.createDirectories(baseDir)

            val zipFileName = "documents_${UUID.randomUUID()}.zip"
            val zipPath = baseDir.resolve(zipFileName)

            // ========================
            // CACHES
            // ========================
            val fileCache = mutableMapOf<String, ByteArray>()
            val converterCache = mutableMapOf<String, FormatConverter?>()
            val exporter = exporters.find { it.supports(info.format.lowercase()) }
                ?: return CompletableFuture.completedFuture(null)

            val total = documents.size
            var processed = 0
            var lastReportedBucket = -1


            val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())


            val futures = documents.map { doc ->
                CompletableFuture.supplyAsync({

                    val replacements = doc.sample.fields.mapNotNull { field ->
                        doc.values.find { it.field.id == field.id }?.let { v ->
                            field.keyName to (v.field.fieldReplaceType to v.value)
                        }
                    }.toMap()


                    val samplePath = doc.sample.filePath
                    val originalBytes = fileCache.getOrPut(samplePath) {
                        val f = fileUtils.getFile(samplePath)
                        if (!f.exists()) return@supplyAsync null
                        f.readBytes()
                    }

                    val ext = samplePath.substringAfterLast('.', "").lowercase()
                    val docxBytes = if (ext == "docx") originalBytes
                    else {
                        val conv = converterCache.getOrPut(ext) { formatConverters.find { it.supports(ext) } }
                        conv?.toFormat(originalBytes) ?: originalBytes
                    }

                    val replacedDocx = factory.replaceFile(ByteArrayInputStream(docxBytes), replacements)
                    val exportedBytes = exporter.export(replacedDocx, replacements)

                    Pair(doc, exportedBytes)
                }, executor)
            }

            FileOutputStream(zipPath.toFile()).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zipStream ->
                        zipStream.setLevel(Deflater.BEST_SPEED)
                        futures.forEach { future ->
                            val (doc, exportedBytes) = future.join() ?: return@forEach
                            val entry = ZipEntry("${doc.name}.${info.format.lowercase()}")
                            zipStream.putNextEntry(entry)
                            zipStream.write(exportedBytes)
                            zipStream.closeEntry()


                            processed++
                            val bucket = (processed * 10) / total
                            if (bucket > lastReportedBucket) {
                                lastReportedBucket = bucket
                                info.status = FileDownloadStatus.PROGRESS
                                downloadInfoRepository.save(info)
                            }
                        }
                    }
                }
            }


            val updatedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
                ?: return CompletableFuture.completedFuture(null)

            updatedInfo.status = FileDownloadStatus.DOWNLOADED
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


//    @Async
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    fun createZipAsync(downloadInfoId: Long, documents: List<Documentation>): CompletableFuture<Void> {
//        val info = waitForDownloadInfo(downloadInfoId)
//            ?: return CompletableFuture.completedFuture(null)
//
//        try {
//            info.status = FileDownloadStatus.DOWNLOADING
//            downloadInfoRepository.save(info)
//
//            val baseDir = Paths.get("uploads", "downloads")
//            Files.createDirectories(baseDir)
//
//            val zipFileName = "documents_${UUID.randomUUID()}.zip"
//            val zipPath = baseDir.resolve(zipFileName)
//
//            // CACHES
//            val fileCache = mutableMapOf<String, ByteArray>()
//            val converterCache = mutableMapOf<String, FormatConverter?>()
//            val exporter = exporters.find { it.supports(info.format.lowercase()) }
//                ?: return CompletableFuture.completedFuture(null)
//
//            // PROGRESS throttling
//            val total = documents.size
//            var processed = 0
//            var lastReportedBucket = -1 // har 10% da xabar beramiz
//
//            FileOutputStream(zipPath.toFile()).use { fos ->
//                BufferedOutputStream(fos).use { bos ->
//                    ZipOutputStream(bos).use { zipStream ->
//                        zipStream.setLevel(Deflater.BEST_SPEED)
//                        documents.forEach { doc ->
//                            // replacements
//                            val replacements = doc.sample.fields.mapNotNull { field ->
//                                doc.values.find { it.field.id == field.id }?.let { v ->
//                                    field.keyName to (v.field.fieldReplaceType to v.value)
//                                }
//                            }.toMap()
//
//                            // original bytes (cached)
//                            val samplePath = doc.sample.filePath
//                            val originalBytes = fileCache.getOrPut(samplePath) {
//                                val f = fileUtils.getFile(samplePath)
//                                if (!f.exists()) return@forEach
//                                f.readBytes()
//                            }
//
//                            // convert to docx if needed (cached converter)
//                            val ext = samplePath.substringAfterLast('.', "").lowercase()
//                            val docxBytes =
//                                if (ext == "docx") originalBytes
//                                else {
//                                    val conv = converterCache.getOrPut(ext) {
//                                        formatConverters.find { it.supports(ext) }
//                                    }
//                                    conv?.toFormat(originalBytes) ?: originalBytes
//                                }
//
//                            // replace & export
//                            val replacedDocx = factory.replaceFile(ByteArrayInputStream(docxBytes), replacements)
//                            val exportedBytes = exporter.export(replacedDocx, replacements)
//
//                            // zip write
//                            val entry = ZipEntry("${doc.name}.${info.format.lowercase()}")
//                            zipStream.putNextEntry(entry)
//                            zipStream.write(exportedBytes)
//                            zipStream.closeEntry()
//
//                            // throttled progress (har ~10% da)
//                            processed++
//                            val bucket = (processed * 10) / total // 0..10
//                            if (bucket > lastReportedBucket) {
//                                lastReportedBucket = bucket
//                                info.status = FileDownloadStatus.PROGRESS
//                                downloadInfoRepository.save(info)
//                            }
//                        }
//                    }
//                }
//            }
//
//            val updatedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
//                ?: return CompletableFuture.completedFuture(null)
//
//            updatedInfo.status = FileDownloadStatus.DOWNLOADED
//            updatedInfo.path = zipPath.toString()
//            downloadInfoRepository.save(updatedInfo)
//
//        } catch (ex: Exception) {
//            val failedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
//                ?: return CompletableFuture.completedFuture(null)
//
//            failedInfo.status = FileDownloadStatus.ERROR
//            failedInfo.path = null
//            log.error("❌ Zip Error: ${ex.message}", ex)
//            downloadInfoRepository.save(failedInfo)
//        }
//
//        return CompletableFuture.completedFuture(null)
//    }

//    @Async
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    fun createZipAsync(downloadInfoId: Long, documents: List<Documentation>): CompletableFuture<Void> {
//        val info = waitForDownloadInfo(downloadInfoId)
//            ?: return CompletableFuture.completedFuture(null)
//
//        try {
//            info.status = FileDownloadStatus.DOWNLOADING
//            downloadInfoRepository.save(info)
//
//            val baseDir = Paths.get("uploads", "downloads")
//            Files.createDirectories(baseDir)
//
//            val zipFileName = "documents_${UUID.randomUUID()}.zip"
//            val zipPath = baseDir.resolve(zipFileName)
//
//            FileOutputStream(zipPath.toFile()).use { fileOut ->
//                ZipOutputStream(fileOut).use { zipStream ->
//                    documents.forEach { doc ->
//                        info.status = FileDownloadStatus.PROGRESS
//                        downloadInfoRepository.save(info)
//                        val replacements = doc.sample.fields.mapNotNull { field ->
//                            doc.values.find { it.field.id == field.id }
//                                ?.let { value -> field.keyName to (value.field.fieldReplaceType to value.value) }
//                        }.toMap()
//
//                        val originalFile = fileUtils.getFile(doc.sample.filePath)
//                        if (!originalFile.exists()) return@forEach
//
//                        val originalBytes = originalFile.readBytes()
//
//                        val ext = doc.sample.filePath.substringAfterLast('.', "").lowercase()
//                        val docxBytes = if (ext == "docx") {
//                            originalBytes
//                        } else {
//                            formatConverters.find { it.supports(ext) }
//                                ?.toFormat(originalBytes) ?: originalBytes
//                        }
//
//                        val replacedDocx = factory.replaceFile(ByteArrayInputStream(docxBytes), replacements)
//                        val exporter = exporters.find { it.supports(info.format.lowercase()) } ?: return@forEach
//                        val exportedBytes = exporter.export(replacedDocx, replacements)
//
//                        val entry = ZipEntry("${doc.name}.${info.format.lowercase()}")
//                        zipStream.putNextEntry(entry)
//                        zipStream.write(exportedBytes)
//                        zipStream.closeEntry()
//                    }
//                }
//            }
//
//            val updatedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
//                ?: return CompletableFuture.completedFuture(null)
//
//            updatedInfo.status = FileDownloadStatus.DOWNLOADED
//            updatedInfo.path = zipPath.toString()
//            downloadInfoRepository.save(updatedInfo)
//
//        } catch (ex: Exception) {
//            val failedInfo = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
//                ?: return CompletableFuture.completedFuture(null)
//
//            failedInfo.status = FileDownloadStatus.ERROR
//            failedInfo.path = null
//            log.error("❌ Zip Error: ${ex.message}", ex)
//            downloadInfoRepository.save(failedInfo)
//        }
//
//        return CompletableFuture.completedFuture(null)
//    }



    fun waitForDownloadInfo(downloadInfoId: Long, maxRetries: Int = 10, delayMs: Long = 500): DownloadInfo? {
        repeat(maxRetries) {
            val info = downloadInfoRepository.findByIdAndDeletedFalse(downloadInfoId)
            if (info != null) return info
            Thread.sleep(delayMs)
        }
        return null
    }
}
