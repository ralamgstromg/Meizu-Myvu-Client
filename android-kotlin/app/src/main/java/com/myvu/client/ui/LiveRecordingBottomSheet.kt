package com.myvu.client.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.myvu.client.R
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.recorder.MeetingAudioRecorder
import java.io.File

class LiveRecordingBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onRecordingSaved(recordingId: Long)
    }

    private var recorder: MeetingAudioRecorder? = null
    private var repository: VoiceRecordingRepository? = null
    var listener: Listener? = null

    private lateinit var etTitle: EditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var tvTime: TextView
    private lateinit var waveformVisualizer: WaveformVisualizerView
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnPauseResume: MaterialButton
    private lateinit var btnStopAndSave: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_record_meeting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = VoiceRecordingRepository(requireContext())
        recorder = MeetingAudioRecorder(requireContext())

        etTitle = view.findViewById(R.id.etRecordingTitle)
        chipGroupCategory = view.findViewById(R.id.chipGroupRecordCategory)
        tvTime = view.findViewById(R.id.tvRecordingTime)
        waveformVisualizer = view.findViewById(R.id.waveformVisualizer)
        btnCancel = view.findViewById(R.id.btnCancelRecording)
        btnPauseResume = view.findViewById(R.id.btnPauseResumeRecording)
        btnStopAndSave = view.findViewById(R.id.btnStopAndSaveRecording)

        setupListeners()
        startActiveRecording()
    }

    private fun setupListeners() {
        recorder?.listener = object : MeetingAudioRecorder.Listener {
            override fun onRecordingStarted(file: File) {
                waveformVisualizer.clearWaveform()
            }

            override fun onRecordingProgress(durationMs: Long, amplitude: Int) {
                activity?.runOnUiThread {
                    val totalSecs = durationMs / 1000
                    val mins = totalSecs / 60
                    val secs = totalSecs % 60
                    tvTime.text = String.format("%02d:%02d", mins, secs)
                    waveformVisualizer.addAmplitude(amplitude)
                }
            }

            override fun onRecordingPaused() {
                activity?.runOnUiThread {
                    btnPauseResume.setIconResource(R.drawable.ic_play_arrow)
                }
            }

            override fun onRecordingResumed() {
                activity?.runOnUiThread {
                    btnPauseResume.setIconResource(R.drawable.ic_pause)
                }
            }

            override fun onRecordingStopped(file: File, durationMs: Long, fileSizeBytes: Long) {
                saveRecordingToDatabase(file, durationMs, fileSizeBytes)
            }

            override fun onRecordingError(message: String, exception: Throwable?) {
                activity?.runOnUiThread {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPauseResume.setOnClickListener {
            if (recorder?.isCurrentlyPaused == true) {
                recorder?.resumeRecording()
            } else {
                recorder?.pauseRecording()
            }
        }

        btnCancel.setOnClickListener {
            recorder?.cancelRecording()
            dismiss()
        }

        btnStopAndSave.setOnClickListener {
            btnStopAndSave.isEnabled = false
            recorder?.stopRecording()
        }
    }

    private fun startActiveRecording() {
        val file = recorder?.startRecording()
        if (file == null) {
            Toast.makeText(context, "No se pudo iniciar el grabador de audio", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun saveRecordingToDatabase(file: File, durationMs: Long, fileSizeBytes: Long) {
        val selectedCategory = when (chipGroupCategory.checkedChipId) {
            R.id.chipRecIdea -> VoiceRecording.CATEGORY_IDEA
            R.id.chipRecConversation -> VoiceRecording.CATEGORY_CONVERSATION
            else -> VoiceRecording.CATEGORY_MEETING
        }

        val inputTitle = etTitle.text?.toString()?.trim()
        val finalTitle = if (!inputTitle.isNullOrBlank()) {
            inputTitle
        } else {
            val catName = when (selectedCategory) {
                VoiceRecording.CATEGORY_IDEA -> "Idea"
                VoiceRecording.CATEGORY_CONVERSATION -> "Conversación"
                else -> "Reunión"
            }
            "$catName ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
        }

        val recording = VoiceRecording(
            title = finalTitle,
            audioPath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            category = selectedCategory,
            status = VoiceRecording.STATUS_READY,
            tags = "audio,reunión"
        )

        val id = repository?.createRecording(recording) ?: -1L
        activity?.runOnUiThread {
            if (id != -1L) {
                listener?.onRecordingSaved(id)
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        if (recorder?.isCurrentlyRecording == true) {
            recorder?.cancelRecording()
        }
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LiveRecordingBottomSheet"

        fun newInstance(): LiveRecordingBottomSheet {
            return LiveRecordingBottomSheet()
        }
    }
}
