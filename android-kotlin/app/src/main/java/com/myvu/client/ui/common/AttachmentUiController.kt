package com.myvu.client.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.core.DocumentExtractor
import com.myvu.client.core.LogBus
import com.myvu.client.database.Attachment
import com.myvu.client.database.AttachmentType
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reusable, decoupled controller for managing attachments across screens:
 * - Camera photo capture
 * - Document picker (Word, Excel, PDF, TXT, Images)
 * - Horizontal card list rendering with thumbnail previews
 * - Attachment removal & details inspection dialogs
 */
class AttachmentUiController(
    private val activity: AppCompatActivity,
    private val btnTakePhoto: MaterialButton?,
    private val btnAttachFile: MaterialButton?,
    private val laySection: LinearLayout,
    private val tvHeader: TextView,
    private val layList: LinearLayout,
    private val onAttachmentAdded: (Attachment) -> Unit,
    private val onAttachmentRemoved: (Attachment) -> Unit,
    private val onAskAiInChat: ((String) -> Unit)? = null
) {

    private var pendingPhotoFile: File? = null

    private val takePhotoLauncher: ActivityResultLauncher<Uri> =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingPhotoFile != null && pendingPhotoFile!!.exists() && pendingPhotoFile!!.length() > 0) {
                activity.lifecycleScope.launch {
                    try {
                        val att = DocumentExtractor.processPhotoFile(activity, pendingPhotoFile!!)
                        onAttachmentAdded(att)
                    } catch (e: Exception) {
                        LogBus.error("AttachmentUiController: Error processing captured photo", e)
                        Toast.makeText(activity, "Error al procesar foto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    private val pickDocumentLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                activity.lifecycleScope.launch {
                    try {
                        val att = DocumentExtractor.processUriAttachment(activity, uri)
                        onAttachmentAdded(att)
                    } catch (e: Exception) {
                        LogBus.error("AttachmentUiController: Error picking document", e)
                        Toast.makeText(activity, "Error al procesar archivo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    init {
        setupActions()
    }

    private fun setupActions() {
        btnTakePhoto?.setOnClickListener {
            try {
                val photosDir = File(activity.getExternalFilesDir(null), "attachments").apply { mkdirs() }
                val photoFile = File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
                pendingPhotoFile = photoFile
                val photoUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", photoFile)
                takePhotoLauncher.launch(photoUri)
            } catch (e: Exception) {
                LogBus.error("AttachmentUiController: Failed to launch camera", e)
                Toast.makeText(activity, "No se pudo abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnAttachFile?.setOnClickListener {
            try {
                val mimeTypes = arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/plain",
                    "text/markdown",
                    "text/csv",
                    "application/json",
                    "image/*"
                )
                pickDocumentLauncher.launch(mimeTypes)
            } catch (e: Exception) {
                LogBus.error("AttachmentUiController: Failed to launch file picker", e)
                Toast.makeText(activity, "No se pudo abrir el selector de archivos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renderAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) {
            laySection.visibility = View.GONE
            return
        }
        laySection.visibility = View.VISIBLE
        tvHeader.text = "📎 Archivos y Fotos Adjuntas (${attachments.size}):"
        layList.removeAllViews()

        val inflater = LayoutInflater.from(activity)
        for (att in attachments) {
            val cardView = inflater.inflate(R.layout.item_attachment_card, layList, false)
            val tvIcon = cardView.findViewById<TextView>(R.id.tvAttachmentIcon)
            val ivThumb = cardView.findViewById<ImageView>(R.id.ivAttachmentThumb)
            val tvName = cardView.findViewById<TextView>(R.id.tvAttachmentName)
            val tvSize = cardView.findViewById<TextView>(R.id.tvAttachmentSize)
            val btnRemove = cardView.findViewById<ImageButton>(R.id.btnRemoveAttachment)

            tvName.text = att.fileName
            val sizeKb = att.fileSizeBytes / 1024
            tvSize.text = "$sizeKb KB • ${att.fileType.name}"

            when (att.fileType) {
                AttachmentType.IMAGE -> {
                    tvIcon.visibility = View.GONE
                    ivThumb.visibility = View.VISIBLE
                    if (att.thumbnailPath != null && File(att.thumbnailPath).exists()) {
                        ivThumb.setImageURI(Uri.fromFile(File(att.thumbnailPath)))
                    } else if (File(att.filePath).exists()) {
                        ivThumb.setImageURI(Uri.fromFile(File(att.filePath)))
                    }
                }
                AttachmentType.PDF -> {
                    if (att.thumbnailPath != null && File(att.thumbnailPath).exists()) {
                        tvIcon.visibility = View.GONE
                        ivThumb.visibility = View.VISIBLE
                        ivThumb.setImageURI(Uri.fromFile(File(att.thumbnailPath)))
                    } else {
                        tvIcon.text = "📄"
                        tvIcon.visibility = View.VISIBLE
                        ivThumb.visibility = View.GONE
                    }
                }
                AttachmentType.WORD -> {
                    tvIcon.text = "📘"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.EXCEL -> {
                    tvIcon.text = "📊"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.TEXT -> {
                    tvIcon.text = "📝"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.OTHER -> {
                    tvIcon.text = "📎"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
            }

            btnRemove.setOnClickListener {
                confirmRemoveAttachment(att)
            }

            cardView.setOnClickListener {
                showAttachmentDetailsDialog(att)
            }

            layList.addView(cardView)
        }
    }

    private fun confirmRemoveAttachment(att: Attachment) {
        AlertDialog.Builder(activity)
            .setTitle("Eliminar Adjunto")
            .setMessage("¿Deseas eliminar '${att.fileName}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                onAttachmentRemoved(att)
                Toast.makeText(activity, "Adjunto eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAttachmentDetailsDialog(att: Attachment) {
        val snippet = att.extractedText.ifBlank { "(Sin texto extraído o formato binario)" }
        val sizeKb = att.fileSizeBytes / 1024

        val msg = "📁 Archivo: ${att.fileName}\n" +
                "📦 Tipo: ${att.fileType.name} ($sizeKb KB)\n\n" +
                "🔍 Contenido extraído para IA:\n" +
                (if (snippet.length > 800) snippet.substring(0, 800) + "...\n[Texto completo disponible para IA]" else snippet)

        AlertDialog.Builder(activity)
            .setTitle("Detalle del Adjunto")
            .setMessage(msg)
            .setPositiveButton("Preguntar en Chat IA") { _, _ ->
                onAskAiInChat?.invoke("Explica o resume el archivo ${att.fileName}")
            }
            .setNeutralButton("Eliminar") { _, _ ->
                confirmRemoveAttachment(att)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
