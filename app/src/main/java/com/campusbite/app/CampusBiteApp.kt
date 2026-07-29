package com.campusbite.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

import com.campusbite.app.BuildConfig

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

@HiltAndroidApp
class CampusBiteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()
    }

    private fun initializeAppCheck() {

        Log.d("CampusBiteAppCheck", "BuildConfig.DEBUG = ${BuildConfig.DEBUG}")

        val provider =
            if (BuildConfig.DEBUG) {
                Log.d("CampusBiteAppCheck","Installing DEBUG provider")
                DebugAppCheckProviderFactory.getInstance()
            } else {
                Log.d("CampusBiteAppCheck","Installing PLAY INTEGRITY provider")
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }

        Firebase.appCheck.installAppCheckProviderFactory(provider)

        Log.d("CampusBiteAppCheck","App Check installed")
    }
}