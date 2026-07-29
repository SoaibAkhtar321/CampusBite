package com.campusbite.app

import android.util.Log
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// ─────────────────────────────────────────────────────────────────
//  DEBUG-variant implementation.
//
//  Lives under src/debug/ so it is only compiled into the debug
//  variant, matching the `debugImplementation` dependency on
//  firebase-appcheck-debug in app/build.gradle.kts. Only one of
//  the debug/release versions of this file is ever on the compile
//  classpath for a given variant, so CampusBiteApp.kt can call
//  provideAppCheckFactory() without directly importing
//  DebugAppCheckProviderFactory (which does not exist on the
//  release compile classpath).
// ─────────────────────────────────────────────────────────────────
internal fun provideAppCheckFactory(): AppCheckProviderFactory {
    Log.d("CampusBiteAppCheck", "Installing DEBUG provider")
    return DebugAppCheckProviderFactory.getInstance()
}