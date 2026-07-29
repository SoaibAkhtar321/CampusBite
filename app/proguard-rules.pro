# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────
#  CampusBite — Firestore POJO keep rules
#
#  Only the model classes below are actually deserialized via
#  DocumentSnapshot.toObject(Class) and/or serialized via
#  CollectionReference/DocumentReference.set(Object) reflection.
#  Verified against actual call sites as of Task 7.2.2:
#
#   - Order        : read  -> AdminViewModel.kt, ShopkeeperViewModel.kt
#   - OrderItem    : read  -> nested field inside Order
#   - Shop         : read  -> HomeViewModel.kt, ShopkeeperProfileViewModel.kt
#   - MenuItem     : read  -> HomeViewModel.kt, ShopkeeperViewModel.kt, ShopRepository.kt
#                    write -> ShopkeeperViewModel.kt (.set(newItem) / .set(menuItem))
#   - User         : read  -> ProfileViewModel.kt, ShopkeeperProfileViewModel.kt
#                    write -> AuthRepository.kt (.set(user))
#   - SlotAvailability : read -> SlotAvailabilityRepository.kt
#
#  CartItem and the Role enum are deliberately NOT included here:
#  CartItem never crosses a Firestore reflection boundary (in-memory
#  UI/cart state only), and Role is never used as a Firestore field
#  type (User.role is a plain String). Adding rules for them would
#  be unnecessary and would silently exempt them from shrinking for
#  no reason.
#
#  Each rule keeps the class name, all fields, the no-arg
#  constructor Firestore requires for construction, and public
#  getter/setter methods (needed for both toObject() field mapping
#  and .set(object) reflection-based writes).
# ─────────────────────────────────────────────────────────────────

-keepclassmembers class com.campusbite.app.data.model.Order {
    <fields>;
    <init>();
    public *;
}
-keep class com.campusbite.app.data.model.Order { <init>(); }

-keepclassmembers class com.campusbite.app.data.model.OrderItem {
    <fields>;
    <init>();

    public *;
}
-keep class com.campusbite.app.data.model.OrderItem { <init>(); }

-keepclassmembers class com.campusbite.app.data.model.Shop {
    <fields>;
    <init>();
    public *;
}
-keep class com.campusbite.app.data.model.Shop { <init>(); }

-keepclassmembers class com.campusbite.app.data.model.MenuItem {
    <fields>;
    <init>();
    public *;
}
-keep class com.campusbite.app.data.model.MenuItem { <init>(); }

-keepclassmembers class com.campusbite.app.data.model.User {
    <fields>;
    <init>();
    public *;
}
-keep class com.campusbite.app.data.model.User { <init>(); }

-keepclassmembers class com.campusbite.app.data.model.SlotAvailability {
    <fields>;
    <init>();
    public *;
}
-keep class com.campusbite.app.data.model.SlotAvailability { <init>(); }
