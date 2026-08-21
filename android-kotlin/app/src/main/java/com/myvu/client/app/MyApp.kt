package com.myvu.client.app

import android.app.Application
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillRegistry

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        SkillRegistry.initialize(this)
        LogBus.log("App started — crash reporter installed & SkillRegistry initialized")
    }
}
