package com.myvu.client.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.myvu.client.R
import com.myvu.client.database.VoiceRecording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceRecordingAdapter(
    private val context: Context,
    private val onItemClick: (VoiceRecording) -> Unit,
    private val onPlayClick: (VoiceRecording) -> Unit,
    private val onDeleteClick: (VoiceRecording) -> Unit,
    private val onReAnalyzeClick: (VoiceRecording) -> Unit,
    private val onShareClick: (VoiceRecording) -> Unit
) : RecyclerView.Adapter<VoiceRecordingAdapter.ViewHolder>() {

    private val recordings = mutableListOf<VoiceRecording>()
    private var currentlyPlayingPath: String? = null
    private var isPlaying: Boolean = false

    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    fun setRecordings(newRecordings: List<VoiceRecording>) {
        recordings.clear()
        recordings.addAll(newRecordings)
        notifyDataSetChanged()
    }

    fun setPlaybackState(audioPath: String?, playing: Boolean) {
        currentlyPlayingPath = audioPath
        isPlaying = playing
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_voice_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = recordings[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = recordings.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardRecording)
        private val tvCategoryBadge: TextView = itemView.findViewById(R.id.tvCategoryBadge)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnMoreOptions: ImageView = itemView.findViewById(R.id.btnMoreOptions)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSummarySnippet: TextView = itemView.findViewById(R.id.tvSummarySnippet)
        private val tvTags: TextView = itemView.findViewById(R.id.tvTags)
        private val btnInlinePlay: MaterialButton = itemView.findViewById(R.id.btnInlinePlay)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(item: VoiceRecording) {
            tvTitle.text = if (item.title.isNotBlank()) item.title else "Grabación sin título"
            tvDate.text = dateFormat.format(Date(item.createdAt))
            tvDuration.text = "⏱️ ${item.formattedDuration()}"

            // Category badge
            when (item.category) {
                VoiceRecording.CATEGORY_MEETING -> {
                    tvCategoryBadge.text = "👥 REUNIÓN"
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.cyber_teal))
                }
                VoiceRecording.CATEGORY_IDEA -> {
                    tvCategoryBadge.text = "💡 IDEA"
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.cyber_teal_light))
                }
                VoiceRecording.CATEGORY_CONVERSATION -> {
                    tvCategoryBadge.text = "🗣️ CONVERSACIÓN"
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.cyber_teal_dim))
                }
                else -> {
                    tvCategoryBadge.text = "🎙️ AUDIO"
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
                }
            }

            // Status badge
            when (item.status) {
                VoiceRecording.STATUS_TRANSCRIBING -> {
                    tvStatusBadge.text = "Transcribiendo..."
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.state_connecting))
                }
                VoiceRecording.STATUS_ANALYZING -> {
                    tvStatusBadge.text = "Analizando IA..."
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.state_connecting))
                }
                VoiceRecording.STATUS_ERROR -> {
                    tvStatusBadge.text = "Error IA"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.state_failed))
                }
                else -> {
                    tvStatusBadge.text = "IA Lista"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.state_ready))
                }
            }

            // Summary snippet or transcript
            val snippet = when {
                item.summary.isNotBlank() -> item.summary.replace(Regex("[#*`_]"), "").trim().take(120)
                item.rawTranscript.isNotBlank() -> item.rawTranscript.take(120)
                else -> "Audio guardado sin procesar."
            }
            tvSummarySnippet.text = snippet

            // Tags
            if (item.tags.isNotBlank()) {
                val tagsText = item.tagsList.joinToString(" ") { "#$it" }
                tvTags.text = tagsText
                tvTags.visibility = View.VISIBLE
            } else {
                tvTags.visibility = View.GONE
            }

            // Play / Pause Inline State
            val isCurrentPlaying = (item.audioPath == currentlyPlayingPath && isPlaying)
            if (isCurrentPlaying) {
                btnInlinePlay.text = "Pausar"
                btnInlinePlay.setIconResource(R.drawable.ic_pause)
            } else {
                btnInlinePlay.text = "Reproducir"
                btnInlinePlay.setIconResource(R.drawable.ic_play_arrow)
            }

            btnInlinePlay.setOnClickListener {
                onPlayClick(item)
            }

            card.setOnClickListener {
                onItemClick(item)
            }

            btnMoreOptions.setOnClickListener { v ->
                showPopupMenu(v, item)
            }
        }

        private fun showPopupMenu(anchor: View, item: VoiceRecording) {
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, 1, 0, "✨ Re-procesar con IA")
            popup.menu.add(0, 2, 1, "📤 Compartir")
            popup.menu.add(0, 3, 2, "🗑️ Eliminar")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onReAnalyzeClick(item)
                    2 -> onShareClick(item)
                    3 -> onDeleteClick(item)
                }
                true
            }
            popup.show()
        }
    }
}
