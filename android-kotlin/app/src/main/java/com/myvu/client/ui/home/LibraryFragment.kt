package com.myvu.client.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
import com.myvu.client.search.SearchIndex
import com.myvu.client.search.SearchIndex.Kind
import com.myvu.client.ui.NoteDetailActivity
import com.myvu.client.ui.NotesActivity
import com.myvu.client.ui.RecordingDetailActivity
import com.myvu.client.ui.VoiceRecorderActivity
import com.myvu.client.ui.chat.ChatActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Biblioteca": notes, meetings, reminders, tasks and chat in one searchable list. */
class LibraryFragment : Fragment() {

    private val adapter = HitAdapter { open(it) }
    private var kinds: Set<Kind> = DEFAULT_KINDS
    private var query = ""
    private var pending: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv: RecyclerView = view.findViewById(R.id.rvLibrary)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val chips: ChipGroup = view.findViewById(R.id.chipsLibrary)
        val filters = listOf("Todo" to DEFAULT_KINDS) + Kind.values().map { "${it.label}s" to setOf(it) }
        filters.forEachIndexed { i, (label, set) ->
            chips.addView(Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = i == 0
                setOnClickListener { kinds = set; refresh(view) }
            })
        }

        view.findViewById<TextInputEditText>(R.id.edtLibrarySearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refresh(view, debounceMs = 250)
            }
        })

        view.findViewById<View>(R.id.btnLibraryNewNote).setOnClickListener {
            startActivity(Intent(requireContext(), NotesActivity::class.java))
        }
        view.findViewById<View>(R.id.btnLibraryRecord).setOnClickListener {
            startActivity(Intent(requireContext(), VoiceRecorderActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View, debounceMs: Long = 0) {
        pending?.cancel()
        pending = viewLifecycleOwner.lifecycleScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val ctx = requireContext().applicationContext
            val hits = if (query.isBlank()) SearchIndex.recent(ctx, kinds) else SearchIndex.search(ctx, query, kinds)
            adapter.submit(hits)
            view.findViewById<TextView>(R.id.txtLibraryEmpty).apply {
                visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
                text = if (query.isBlank()) "Todavía no hay contenido. Crea una nota o graba una reunión." else "Sin resultados para «$query»"
            }
        }
    }

    private fun open(hit: SearchIndex.Hit) {
        val ctx = requireContext()
        val id = hit.refId.toLongOrNull() ?: -1L
        val intent = when (hit.kind) {
            Kind.NOTE -> Intent(ctx, NoteDetailActivity::class.java)
                .putExtra(NoteDetailActivity.EXTRA_ITEM_TYPE, NoteDetailActivity.TYPE_NOTE)
                .putExtra(NoteDetailActivity.EXTRA_ITEM_ID, id)
            Kind.REMINDER -> Intent(ctx, NoteDetailActivity::class.java)
                .putExtra(NoteDetailActivity.EXTRA_ITEM_TYPE, NoteDetailActivity.TYPE_REMINDER)
                .putExtra(NoteDetailActivity.EXTRA_ITEM_ID, id)
            Kind.RECORDING -> Intent(ctx, RecordingDetailActivity::class.java)
                .putExtra(RecordingDetailActivity.EXTRA_RECORDING_ID, id)
            Kind.TODO -> Intent(ctx, NotesActivity::class.java)
            Kind.CHAT -> Intent(ctx, ChatActivity::class.java)
        }
        startActivity(intent)
    }

    private class HitAdapter(private val onClick: (SearchIndex.Hit) -> Unit) : RecyclerView.Adapter<HitAdapter.Holder>() {
        private var items: List<SearchIndex.Hit> = emptyList()

        fun submit(list: List<SearchIndex.Hit>) {
            items = list
            notifyDataSetChanged()
        }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val meta: TextView = v.findViewById(R.id.txtHitMeta)
            val title: TextView = v.findViewById(R.id.txtHitTitle)
            val snippet: TextView = v.findViewById(R.id.txtHitSnippet)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_library_hit, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val hit = items[position]
            val date = if (hit.date > 0) DateUtils.getRelativeTimeSpanString(hit.date).toString() else ""
            holder.meta.text = listOf(hit.kind.label, date).filter { it.isNotBlank() }.joinToString(" · ")
            holder.title.text = hit.title
            holder.snippet.text = hit.snippet
            holder.snippet.visibility = if (hit.snippet.isBlank()) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener { onClick(hit) }
        }
    }

    companion object {
        /** Chat lines are searchable but left out of "Todo" so they don't flood recent items. */
        private val DEFAULT_KINDS = setOf(Kind.NOTE, Kind.RECORDING, Kind.REMINDER, Kind.TODO)
    }
}
