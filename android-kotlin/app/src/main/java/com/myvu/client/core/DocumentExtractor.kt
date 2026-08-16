package com.myvu.client.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.myvu.client.database.Attachment
import com.myvu.client.database.AttachmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Universal document parser and text extractor:
 * - Word (.docx) XML extractor
 * - Excel (.xlsx) tabular extractor
 * - Plain text / Markdown / JSON / CSV
 * - PDF thumbnail and text extractor
 * - Image thumbnail generator
 */
object DocumentExtractor {

    private const val MAX_EXTRACTED_CHARS = 30000

    suspend fun processUriAttachment(
        context: Context,
        uri: Uri
    ): Attachment = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val fileName = getFileNameFromUri(appContext, uri) ?: "adjunto_${System.currentTimeMillis()}"
        val mimeType = appContext.contentResolver.getType(uri)
        val fileType = Attachment.detectType(fileName, mimeType)

        val attachmentsDir = File(appContext.getExternalFilesDir(null), "attachments").apply { mkdirs() }
        val ext = fileName.substringAfterLast('.', "")
        val safeExt = if (ext.isNotBlank()) ".$ext" else ""
        val targetFile = File(attachmentsDir, "${UUID.randomUUID()}$safeExt")

        // Copy file locally
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        var extractedText = ""
        var thumbnailPath: String? = null

        try {
            when (fileType) {
                AttachmentType.TEXT -> {
                    extractedText = extractPlainText(targetFile)
                }
                AttachmentType.WORD -> {
                    extractedText = extractDocxText(targetFile)
                }
                AttachmentType.EXCEL -> {
                    extractedText = extractXlsxText(targetFile)
                }
                AttachmentType.PDF -> {
                    extractedText = extractPdfText(targetFile)
                    thumbnailPath = generatePdfThumbnail(appContext, targetFile)
                }
                AttachmentType.IMAGE -> {
                    thumbnailPath = generateImageThumbnail(appContext, targetFile)
                    extractedText = "[Imagen adjunta: $fileName]"
                }
                AttachmentType.OTHER -> {
                    extractedText = "[Archivo adjunto: $fileName]"
                }
            }
        } catch (e: Exception) {
            LogBus.error("DocumentExtractor -> Extraction failed for $fileName", e)
            extractedText = "[Error al extraer contenido: ${e.message}]"
        }

        if (extractedText.length > MAX_EXTRACTED_CHARS) {
            extractedText = extractedText.substring(0, MAX_EXTRACTED_CHARS) + "\n... [Texto truncado por longitud]"
        }

        Attachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            filePath = targetFile.absolutePath,
            fileType = fileType,
            fileSizeBytes = targetFile.length(),
            extractedText = extractedText.trim(),
            thumbnailPath = thumbnailPath,
            createdAt = System.currentTimeMillis()
        )
    }

    suspend fun processPhotoFile(
        context: Context,
        photoFile: File
    ): Attachment = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val thumbnailPath = generateImageThumbnail(appContext, photoFile)
        val fileName = "Foto_${System.currentTimeMillis()}.jpg"

        Attachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            filePath = photoFile.absolutePath,
            fileType = AttachmentType.IMAGE,
            fileSizeBytes = photoFile.length(),
            extractedText = "[Fotografía capturada con la cámara: $fileName]",
            thumbnailPath = thumbnailPath,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun extractPlainText(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Extracts text from .docx by unzipping and parsing word/document.xml
     */
    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zis.bufferedReader(Charsets.UTF_8).readText()
                    val text = parseDocxXml(xmlContent)
                    sb.append(text)
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun parseDocxXml(xml: String): String {
        val sb = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") { // Text node
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) {
                            sb.append(parser.text)
                        }
                    } else if (parser.name == "p") { // Paragraph start
                        if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                            sb.append("\n")
                        }
                    } else if (parser.name == "tab") {
                        sb.append("\t")
                    } else if (parser.name == "br") {
                        sb.append("\n")
                    }
                }
            }
            eventType = parser.next()
        }
        return sb.toString()
    }

    /**
     * Extracts tables and text from .xlsx by parsing xl/sharedStrings.xml & xl/worksheets/sheet1.xml
     */
    private fun extractXlsxText(file: File): String {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    val xml = zis.bufferedReader(Charsets.UTF_8).readText()
                    sharedStrings.addAll(parseXlsxSharedStrings(xml))
                } else if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetXml = zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }

        if (sheetXml != null) {
            return parseXlsxSheet(sheetXml!!, sharedStrings)
        }
        return ""
    }

    private fun parseXlsxSharedStrings(xml: String): List<String> {
        val list = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var currentText = StringBuilder()
        var inStringItem = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inStringItem = true
                        currentText = StringBuilder()
                    } else if (parser.name == "t" && inStringItem) {
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) {
                            currentText.append(parser.text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        inStringItem = false
                        list.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseXlsxSheet(xml: String, sharedStrings: List<String>): String {
        val sb = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var currentRow = mutableListOf<String>()
        var currentCellType = ""
        var currentCellValue = ""
        var inRow = false
        var inCell = false
        var inValue = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            inRow = true
                            currentRow = mutableListOf()
                        }
                        "c" -> { // cell
                            inCell = true
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellValue = ""
                        }
                        "v" -> { // value
                            inValue = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) {
                        currentCellValue += parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValue = false
                        "c" -> {
                            inCell = false
                            val resolvedText = if (currentCellType == "s") {
                                val idx = currentCellValue.toIntOrNull() ?: -1
                                if (idx in 0 until sharedStrings.size) sharedStrings[idx] else currentCellValue
                            } else {
                                currentCellValue
                            }
                            currentRow.add(resolvedText.trim())
                        }
                        "row" -> {
                            inRow = false
                            if (currentRow.any { it.isNotBlank() }) {
                                sb.append("| ").append(currentRow.joinToString(" | ")).append(" |\n")
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return sb.toString()
    }

    /**
     * Extracts text from PDF using stream scanning
     */
    private fun extractPdfText(file: File): String {
        val sb = StringBuilder()
        try {
            val bytes = file.readBytes()
            val text = String(bytes, Charsets.ISO_8859_1)

            // Extract text enclosed in parentheses inside BT ... ET text blocks
            val btRegex = Regex("""BT\s*([\s\S]*?)\s*ET""")
            val tjRegex = Regex("""\(([\s\S]*?)\)\s*Tj""")
            val arrayRegex = Regex("""\[([\s\S]*?)\]\s*TJ""")

            val matches = btRegex.findAll(text)
            for (m in matches) {
                val block = m.groupValues[1]
                for (tj in tjRegex.findAll(block)) {
                    sb.append(tj.groupValues[1]).append(" ")
                }
                for (arr in arrayRegex.findAll(block)) {
                    val rawArray = arr.groupValues[1]
                    val innerTj = Regex("""\(([\s\S]*?)\)""").findAll(rawArray)
                    for (item in innerTj) {
                        sb.append(item.groupValues[1])
                    }
                    sb.append(" ")
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            LogBus.error("DocumentExtractor -> PDF stream extraction failed", e)
        }

        if (sb.isBlank()) {
            return "[Documento PDF: ${file.name}]"
        }
        return sb.toString().trim()
    }

    private fun generatePdfThumbnail(context: Context, file: File): String? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val width = 300
                val height = (300f * page.height / page.width).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()

                val thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                val thumbFile = File(thumbsDir, "thumb_pdf_${UUID.randomUUID()}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                thumbFile.absolutePath
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateImageThumbnail(context: Context, file: File): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > 400 || options.outHeight / sampleSize > 400) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
            val thumbFile = File(thumbsDir, "thumb_img_${UUID.randomUUID()}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            thumbFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) return cursor.getString(nameIdx)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
