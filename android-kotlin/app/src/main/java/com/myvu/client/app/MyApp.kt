package com.myvu.client.app

import android.app.Application
import com.myvu.client.core.LogBus

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        LogBus.log("App started — crash reporter installed")
    }
}
