# ─────────────────────────────────────────────────────────────────
#  CampusBite — Release R8 / ProGuard rules
#  Audit 7.2 — Secure Release Build Configuration
#
#  Goal: enable safe code + resource shrinking without breaking
#  runtime reflection used by Firestore (toObject), Hilt/Dagger
#  generated code, Kotlin reflection metadata, or Compose.
# ─────────────────────────────────────────────────────────────────

# ── General attribute preservation (required for correct
#    reflection, generics, and Crashlytics symbolication) ─────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# Keep source file + line numbers for readable (deobfuscated)
# Crashlytics stack traces, but strip the real file name so the
# obfuscation mapping is still required to read it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────
#  Firestore data models
#
#  These classes are deserialized reflectively via
#  DocumentSnapshot.toObject(X::class.java) in ProfileViewModel,
#  AdminViewModel, HomeViewModel, ShopkeeperViewModel,
#  ShopkeeperProfileViewModel, ShopRepository, and
#  SlotAvailabilityRepository. R8 must not rename/strip their
#  fields, no-arg constructors, or getters/setters, and must not
#  remove the classes as "unused" since they're only referenced by
#  reflection at runtime.
# ─────────────────────────────────────────────────────────────────
-keep class com.campusbite.app.data.model.** {
    <init>();
    <fields>;
    <methods>;
}

# Order.kt parses documents manually (Order.from(snapshot)) rather
# than via toObject(), but is still included above for consistency
# and forward-compatibility — do not remove.

# ─────────────────────────────────────────────────────────────────
#  Firebase (Auth, Firestore, Functions, Messaging, Crashlytics,
#  App Check). Firebase SDKs ship their own consumer ProGuard
#  rules via AAR, but the rules below add explicit defense-in-depth
#  for the gRPC / Protobuf / Guava internals Firestore relies on.
# ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# gRPC / Protobuf (Firestore transport layer)
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Guava (used internally by Firestore/gRPC)
-dontwarn com.google.common.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn sun.misc.Unsafe

# ─────────────────────────────────────────────────────────────────
#  Hilt / Dagger
#
#  Hilt's own consumer rules keep the vast majority of generated
#  code, but keep the entry points explicitly for safety.
# ─────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltModules { *; }
-keep class **Hilt_* { *; }
-keep class *_Factory { *; }
-keep class *_MembersInjector { *; }
-dontwarn dagger.hilt.**

# Application / Service entry points referenced from the manifest
-keep class com.campusbite.app.CampusBiteApp { *; }
-keep class com.campusbite.app.messaging.CampusBiteMessagingService { *; }
-keep class com.campusbite.app.MainActivity { *; }

# ─────────────────────────────────────────────────────────────────
#  Kotlin reflection / coroutines / enums
# ─────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# Enum values()/valueOf() must survive shrinking (e.g. Role)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─────────────────────────────────────────────────────────────────
#  Jetpack Compose
#
#  AndroidX Compose libraries bundle their own consumer rules; the
#  entries below are narrow safety nets and do not disable
#  shrinking of the Compose runtime itself.
# ─────────────────────────────────────────────────────────────────
-keep class androidx.compose.runtime.Composer { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# ─────────────────────────────────────────────────────────────────
#  OkHttp / Okio (transitive, used directly + by Firebase)
# ─────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─────────────────────────────────────────────────────────────────
#  ZXing (QR code)
# ─────────────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ─────────────────────────────────────────────────────────────────
#  Credential Manager / Google Sign-In (GoogleIdTokenCredential is
#  parsed via reflection-adjacent Bundle parcels)
# ─────────────────────────────────────────────────────────────────
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# ─────────────────────────────────────────────────────────────────
#  Standard Android WebView JS interface guard (kept from template,
#  not currently used but harmless to retain).
# ─────────────────────────────────────────────────────────────────
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}