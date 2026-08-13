package com.myvu.client.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class LogAdapter(context: Context) : RecyclerView.Adapter<LogAdapter.Row>() {

    companion object {
        private const val COLOR_CYBER_TEAL = 0xFF00F0FF.toInt() // AI / System
        private const val COLOR_GREEN = 0xFF2ECC71.toInt()      // Connection OK
        private const val COLOR_AMBER = 0xFFF5A623.toInt()      // Sync / Warn
        private const val COLOR_RED = 0xFFFFB4AB.toInt()        // Errors
    }

    private val lines: MutableList<String> = ArrayList()
    private val textColor: Int

    init {
        val tv = TypedValue()
        val ok = context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)
        this.textColor = if (ok) ContextCompat.getColor(context, tv.resourceId) else COLOR_CYBER_TEAL
    }

    fun setAll(newLines: List<String>) {
        lines.clear()
        lines.addAll(newLines)
        notifyDataSetChanged()
    }

    fun add(line: String): Int {
        lines.add(line)
        notifyItemInserted(lines.size - 1)
        return lines.size - 1
    }

    fun clear() {
        val n = lines.size
        lines.clear()
        notifyItemRangeRemoved(0, n)
    }

    fun size(): Int = lines.size

    override fun getItemCount(): Int = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val ctx = parent.context
        val tv = TextView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_CYBER_TEAL)
            setPadding(0, 3, 0, 3)
        }
        return Row(tv)
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        val tv = holder.itemView as TextView
        val line = lines[position]
        tv.text = line
        tv.setTextColor(getLineColor(line))
    }

    private fun getLineColor(line: String): Int {
        val lower = line.lowercase()
        return when {
            lower.contains("error") || lower.contains("failed") || lower.contains("failure") ||
                lower.contains("exception") || lower.contains("fatal") || lower.contains("could not") ||
                lower.contains("err:") || lower.contains("[e]") -> COLOR_RED

            lower.contains("connected") || lower.contains("connection ok") || lower.contains("ready") ||
                lower.contains("bound") || lower.contains("success") || lower.contains("established") -> COLOR_GREEN

            lower.contains("!!") || lower.contains("warn") || lower.contains("warning") ||
                lower.contains("sync") || lower.contains("retry") || lower.contains("retrying") ||
                lower.contains("pending") -> COLOR_AMBER

            else -> COLOR_CYBER_TEAL
        }
    }

    class Row(v: TextView) : RecyclerView.ViewHolder(v)
}

