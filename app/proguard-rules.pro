# Kiaraen ProGuard rules
# kotlinx-serialization, Room and supabase-kt all ship consumer rules.

# Keep DTO property names used by kotlinx-serialization <-> PostgREST JSON
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep BuildConfig (supabase url/key) intact — default is fine, listed for clarity.
-keep class com.kiaraen.app.BuildConfig { *; }

# ktor pulls slf4j-api; the optional StaticLoggerBinder lookup must not fail R8
-dontwarn org.slf4j.**
