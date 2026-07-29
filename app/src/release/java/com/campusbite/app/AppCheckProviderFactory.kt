package com.campusbite.app

import android.util.Log
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// ─────────────────────────────────────────────────────────────────
//  RELEASE-variant implementation.
//
//  Lives under src/release/ so it is only compiled into the
//  release variant. Only one of the debug/release versions of this
//  file is ever on the compile classpath for a given variant, so
//  CampusBiteApp.kt can call provideAppCheckFactory() without
//  branching on BuildConfig.DEBUG or importing anything debug-only.
// ─────────────────────────────────────────────────────────────────
internal fun provideAppCheckFactory(): AppCheckProviderFactory {
    Log.d("CampusBiteAppCheck", "Installing PLAY INTEGRITY provider")
    return PlayIntegrityAppCheckProviderFactory.getInstance()
}