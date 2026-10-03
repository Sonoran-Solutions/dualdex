package com.dualdex

import android.app.Application
import android.util.Log

class DualDexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.dualdex.coverage.HnsCoverageFactory.initialize(this)
        Log.i("DualDex", "DualDex Application initialized")
    }
}
