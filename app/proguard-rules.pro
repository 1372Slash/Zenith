# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Moshi: presets/alarms/variants are parsed via KotlinJsonAdapterFactory
# (reflection over data-class fields). Without these, R8 renames/strips the
# fields and parsing silently falls back to empty defaults in release builds.
-keepclasseswithmembernames class * {
    @com.squareup.moshi.JsonQualifier <methods>;
}
-keep @com.squareup.moshi.JsonClass class *
-keep class com.etrisad.zenith.data.model.** { *; }
-keep class com.etrisad.zenith.ui.components.pausepoint.** { *; }
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn kotlin.reflect.**

# Room entities/DAOs are referenced by generated code; keep the schema
# classes visible for validation and migration checks.
-keep class com.etrisad.zenith.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase
