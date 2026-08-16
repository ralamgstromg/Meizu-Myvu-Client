package com.myvu.client.database

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.util.UUID

enum class AttachmentType {
    IMAGE,
    PDF,
    WORD,
    EXCEL,
    TEXT,
    OTHER
}

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileType: AttachmentType,
    val fileSizeBytes: Long = 0L,
    val extractedText: String = "",
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("file_name", fileName)
            put("file_path", filePath)
            put("file_type", fileType.name)
            put("file_size_bytes", fileSizeBytes)
            put("extracted_text", extractedText)
            put("thumbnail_path", thumbnailPath ?: "")
            put("created_at", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Attachment {
            val typeStr = json.optString("file_type", "OTHER")
            val type = try {
                AttachmentType.valueOf(typeStr)
            } catch (e: Exception) {
                AttachmentType.OTHER
            }
            return Attachment(
                id = json.optString("id", UUID.randomUUID().toString()),
                fileName = json.optString("file_name", "archivo"),
                filePath = json.optString("file_path", ""),
                fileType = type,
                fileSizeBytes = json.optLong("file_size_bytes", 0L),
                extractedText = json.optString("extracted_text", ""),
                thumbnailPath = json.optString("thumbnail_path", "").takeIf { it.isNotBlank() },
                createdAt = json.optLong("created_at", System.currentTimeMillis())
            )
        }

        fun listToJson(attachments: List<Attachment>): String {
            val array = JSONArray()
            for (att in attachments) {
                array.put(att.toJson())
            }
            return array.toString()
        }

        fun listFromJson(jsonStr: String?): List<Attachment> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<Attachment>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(fromJson(obj))
                }
            } catch (ignored: Exception) {}
            return list
        }

        fun detectType(fileName: String, mimeType: String? = null): AttachmentType {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                        lower.endsWith(".webp") || lower.endsWith(".gif") || mimeType?.startsWith("image/") == true -> AttachmentType.IMAGE
                lower.endsWith(".pdf") || mimeType == "application/pdf" -> AttachmentType.PDF
                lower.endsWith(".docx") || lower.endsWith(".doc") ||
                        mimeType?.contains("word") == true -> AttachmentType.WORD
                lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv") ||
                        mimeType?.contains("spreadsheet") == true || mimeType?.contains("excel") == true -> AttachmentType.EXCEL
                lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") ||
                        lower.endsWith(".log") || mimeType?.startsWith("text/") == true -> AttachmentType.TEXT
                else -> AttachmentType.OTHER
            }
        }
    }
}
