package com.example.diceroller

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class DiceApp : Application() {

    // App-scoped ON PURPOSE: the repository must outlive every Activity.
    val repo: DiceDataStore by lazy { DiceDataStore(this) }

    /** Single ViewModel factory — swap DiceRepository for a fake in tests. */
    val vmFactory = viewModelFactory {
        initializer { DiceViewModel(repo) }
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()

        // Crash forensics: keep last crash locally, log it on next launch.
        Thread.setDefaultUncaughtExceptionHandler(CrashGuard(filesDir))
        CrashGuard.consumePrevious(filesDir)?.let {
            Log.w("DiceApp", "Previous session crashed:\n$it")
            // Production hook: reporter.capture(it)  (Sentry / Crashlytics)
        }

        // Populate the synchronous theme mirror before any Activity reads it.
        repo.attachMirror()
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog().build()
        )
    }
}
