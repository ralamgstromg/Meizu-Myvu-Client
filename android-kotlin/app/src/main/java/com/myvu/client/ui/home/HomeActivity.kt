package com.myvu.client.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.myvu.client.R

/**
 * App entry point: four tabs (Hoy, Biblioteca, Agente, Ajustes). Feature screens
 * (chat, notes, recordings, devices, settings) are opened from the tabs.
 */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val nav: BottomNavigationView = findViewById(R.id.homeBottomNav)
        nav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.navLibrary -> LibraryFragment()
                    R.id.navAgent -> AgentFragment()
                    R.id.navMore -> MoreFragment()
                    else -> TodayFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = intent.getIntExtra(EXTRA_TAB, R.id.navToday)
            // First launch: explain permissions in one place instead of a burst of system dialogs.
            if (com.myvu.client.ui.PermissionsActivity.shouldShowOnFirstLaunch(this)) {
                startActivity(android.content.Intent(this, com.myvu.client.ui.PermissionsActivity::class.java))
            }
        }
    }

    /** Switches tab from inside a fragment (e.g. "Rutinas" quick action). */
    fun selectTab(itemId: Int) {
        findViewById<BottomNavigationView>(R.id.homeBottomNav).selectedItemId = itemId
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.homeContainer, fragment)
            .commit()
    }

    companion object {
        const val EXTRA_TAB = "EXTRA_TAB"
    }
}
