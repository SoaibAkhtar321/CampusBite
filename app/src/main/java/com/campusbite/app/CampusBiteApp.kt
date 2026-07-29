package com.campusbite.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck

@HiltAndroidApp
class CampusBiteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()
    }

    private fun initializeAppCheck() {
        // provideAppCheckFactory() is variant-specific: the debug
        // implementation lives in src/debug/, the release
        // implementation in src/release/. Exactly one is compiled
        // into any given build, so this file never needs to import
        // DebugAppCheckProviderFactory directly (that class does
        // not exist on the release compile classpath).
        val provider = provideAppCheckFactory()

        Firebase.appCheck.installAppCheckProviderFactory(provider)
    }
}