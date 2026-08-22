package com.adityanotes

import android.app.Application
import com.adityanotes.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AdityaNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Application-level logging initialization.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Future database initialization.
        // Future DataStore/preferences initialization.
        // Future analytics initialization.
    }
}