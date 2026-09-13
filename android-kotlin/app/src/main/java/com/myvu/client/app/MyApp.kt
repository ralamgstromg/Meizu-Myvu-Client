package com.myvu.client.app

import android.app.Application
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillRegistry

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.myvu.client.core.Prefs.applyTheme(this)
        CrashReporter.install(this)
        SkillRegistry.initialize(this)
        com.myvu.client.health.HealthService.getInstance(this)
        if (com.myvu.client.core.Prefs.autoReconnectEnabled(this)) {
            com.myvu.client.service.ServiceWatchdogReceiver.scheduleWatchdog(this)
            LogBus.log("App started — crash reporter installed, SkillRegistry initialized & watchdog scheduled")
        } else {
            LogBus.log("App started — crash reporter installed, SkillRegistry initialized (auto-reconnect disabled)")
        }
    }
}
