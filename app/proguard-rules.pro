# Kiaraen ProGuard rules
# kotlinx-serialization, Room and supabase-kt all ship consumer rules.

# Keep DTO property names used by kotlinx-serialization <-> PostgREST JSON
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Belt and braces for our own @Serializable DTOs (reified decodeList<T>()).
-keepclassmembers class com.kiaraen.app.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.kiaraen.app.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kiaraen.app.data.remote.**$$serializer { *; }

# Keep BuildConfig (supabase url/key) intact — default is fine, listed for clarity.
-keep class com.kiaraen.app.BuildConfig { *; }

# ktor pulls slf4j-api; the optional StaticLoggerBinder lookup must not fail R8
-dontwarn org.slf4j.**
# ktor's debug detector references JVM-only management APIs
-dontwarn java.lang.management.**
