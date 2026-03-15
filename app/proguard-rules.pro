# KiteWatch ProGuard / R8 Rules
# Rules are additive. Only add rules when R8 strips something it should keep.
# Verify after every rule addition with: ./gradlew assembleRelease + smoke test on device.

# ==================== Room ====================
# Keep @Entity, @Dao, and RoomDatabase subclasses from being renamed/removed.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# ==================== Moshi ====================
# Keep @JsonClass generated adapters and @Json annotated fields.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ==================== Hilt ====================
# Hilt-generated component classes. Hilt's own rules cover most cases;
# this guards against edge-cases in full-mode R8.
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ==================== Retrofit ====================
# Retrofit service interfaces and their generic Call/Response wrappers.
-keep interface retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ==================== WorkManager ====================
# Keep Worker and CoroutineWorker subclasses (instantiated by class name).
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ==================== Kite Connect DTOs ====================
# Keep all network DTO classes to prevent field-name obfuscation breaking JSON parsing.
-keep class com.kitewatch.core.network.dto.** { *; }

# ==================== Kotlin ====================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ==================== Coroutines ====================
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==================== SQLCipher ====================
# Keep all SQLCipher classes — the native JNI bridge relies on exact class/method names.
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**
