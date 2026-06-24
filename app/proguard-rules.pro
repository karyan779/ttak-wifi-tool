# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the line number information for debugging stack traces (obfuscated)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retain annotations and signatures for reflection-based frameworks (like Retrofit/Moshi)
-keepattributes Signature,InnerClasses,EnclosingMethod,AnnotationDefault,*Annotation*

# Jetpack Compose Rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}

# Room Database Rules
-dontwarn androidx.room.**
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
    @androidx.room.Database *;
}
-keep class * {
    @androidx.room.Entity *;
}

# Moshi Rules for JSON serialization/deserialization
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keep class *JsonAdapter { *; }
-keep class *$$JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <fields>;
    <init>(...);
}

# Retrofit & OkHttp Rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.** <methods>;
}
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep JVM-specific or external platform missing references (e.g. Ktor or other JVM dependencies)
-dontwarn java.lang.management.**
-dontwarn javax.management.**

# Keep view models so they can be instantiated dynamically by Compose Lifecycle
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

