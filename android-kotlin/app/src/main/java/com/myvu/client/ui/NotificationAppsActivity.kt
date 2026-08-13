package com.myvu.client.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.myvu.client.R
import com.myvu.client.core.Prefs
import java.text.Collator
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

class NotificationAppsActivity : AppCompatActivity() {

    private val allowed = HashSet<String>()
    private lateinit var list: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var summary: TextView
    private lateinit var txtSearch: EditText
    private var allAppRows: List<AppRow> = emptyList()
    private var adapter: AppsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_apps)

        list = findViewById(R.id.rvApps)
        progress = findViewById(R.id.appsProgress)
        summary = findViewById(R.id.txtAppsSummary)
        txtSearch = findViewById(R.id.txtSearch)
        list.layoutManager = LinearLayoutManager(this)

        allowed.addAll(Prefs.allowedPackages(this))
        findViewById<View>(R.id.btnAppsBack).setOnClickListener { finish() }

        txtSearch.doOnTextChanged { text, _, _, _ ->
            filterApps(text?.toString() ?: "")
        }

        loadApps()
    }

    private fun loadApps() {
        progress.visibility = View.VISIBLE
        val main = Handler(Looper.getMainLooper())
        Thread({
            val rows = queryApps()
            main.post {
                progress.visibility = View.GONE
                allAppRows = rows
                adapter = AppsAdapter(rows.toMutableList())
                list.adapter = adapter
                filterApps(txtSearch.text.toString())
                updateSummary()
            }
        }, "myvu-appscan").start()
    }

    private fun filterApps(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allAppRows
        } else {
            allAppRows.filter {
                it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
            }
        }
        adapter?.updateList(filtered)
    }

    private fun queryApps(): List<AppRow> {
        val pm = packageManager
        val blocked = Prefs.blockedPackages(this)

        val seen = HashSet<String>()
        val rows = ArrayList<AppRow>()

        // 1. Query launcher activities
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launchable, 0)

        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            if (!seen.add(ai.packageName)) continue
            if (blocked.contains(ai.packageName)) continue
            val label = pm.getApplicationLabel(ai).toString()
            val icon = try { ai.loadIcon(pm) } catch (e: Exception) { pm.defaultActivityIcon }
            rows.add(AppRow(ai.packageName, label, icon))
        }

        // 2. Query all installed applications to catch non-launcher notification senders
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList<ApplicationInfo>()
        }

        for (ai in installed) {
            if (!seen.add(ai.packageName)) continue
            if (blocked.contains(ai.packageName)) continue

            // Include all user-installed apps or updated system apps
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Skip internal OS system-only packages without launcher intents unless user-installed
            if (isSystem && !isUpdatedSystem) continue

            val label = pm.getApplicationLabel(ai).toString()
            val icon = try { ai.loadIcon(pm) } catch (e: Exception) { pm.defaultActivityIcon }
            rows.add(AppRow(ai.packageName, label, icon))
        }

        val collator = Collator.getInstance()
        Collections.sort(rows) { a, b ->
            val sa = allowed.contains(a.pkg)
            val sb = allowed.contains(b.pkg)
            if (sa != sb) if (sa) -1 else 1 else collator.compare(a.label, b.label)
        }
        return rows
    }

    private fun updateSummary() {
        summary.text = if (allowed.isEmpty()) {
            "No apps selected — nothing is mirrored to the glasses yet."
        } else {
            "${allowed.size} app${if (allowed.size == 1) "" else "s"} will mirror notifications to the glasses."
        }
    }

    private class AppRow(
        val pkg: String,
        val label: String,
        val icon: Drawable
    )

    private inner class AppsAdapter(private val rows: MutableList<AppRow>) : RecyclerView.Adapter<AppsAdapter.Row>() {

        fun updateList(newList: List<AppRow>) {
            rows.clear()
            rows.addAll(newList)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
            return Row(
                LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            )
        }

        override fun onBindViewHolder(holder: Row, position: Int) {
            val row = rows[position]
            holder.itemView.findViewById<ImageView>(R.id.appIcon).setImageDrawable(row.icon)
            holder.itemView.findViewById<TextView>(R.id.appLabel).text = row.label
            val sw = holder.itemView.findViewById<MaterialSwitch>(R.id.appSwitch)
            sw.isChecked = allowed.contains(row.pkg)
            holder.itemView.setOnClickListener {
                val on = !allowed.contains(row.pkg)
                if (on) allowed.add(row.pkg) else allowed.remove(row.pkg)
                sw.isChecked = on
                Prefs.setAllowedPackages(this@NotificationAppsActivity, allowed)
                updateSummary()
            }
        }

        inner class Row(v: View) : RecyclerView.ViewHolder(v)
    }
}
