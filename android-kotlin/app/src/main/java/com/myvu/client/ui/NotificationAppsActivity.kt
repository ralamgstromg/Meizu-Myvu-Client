package com.myvu.client.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_apps)

        list = findViewById(R.id.rvApps)
        progress = findViewById(R.id.appsProgress)
        summary = findViewById(R.id.txtAppsSummary)
        list.layoutManager = LinearLayoutManager(this)

        allowed.addAll(Prefs.allowedPackages(this))
        findViewById<View>(R.id.btnAppsBack).setOnClickListener { finish() }

        loadApps()
    }

    private fun loadApps() {
        progress.visibility = View.VISIBLE
        val main = Handler(Looper.getMainLooper())
        Thread({
            val rows = queryApps()
            main.post {
                progress.visibility = View.GONE
                list.adapter = AppsAdapter(rows)
                updateSummary()
            }
        }, "myvu-appscan").start()
    }

    private fun queryApps(): List<AppRow> {
        val pm = packageManager
        val blocked = Prefs.blockedPackages(this)
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launchable, 0)

        val seen = HashSet<String>()
        val rows = ArrayList<AppRow>()
        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            if (!seen.add(ai.packageName)) continue
            if (blocked.contains(ai.packageName)) continue
            rows.add(AppRow(ai.packageName, pm.getApplicationLabel(ai).toString(), ai.loadIcon(pm)))
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

    private inner class AppsAdapter(private val rows: List<AppRow>) : RecyclerView.Adapter<AppsAdapter.Row>() {

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
