keepclasseswithmembernames class * {
    @com.squareup.moshi.JsonQualifier <methods>;
}
-keep @com.squareup.moshi.JsonClass class *
-keep class com.etrisad.zenith.data.model.** { *; }
-keep class com.etrisad.zenith.ui.components.pausepoint.** { *; }
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn kotlin.reflect.**
-keep class com.etrisad.zenith.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase
