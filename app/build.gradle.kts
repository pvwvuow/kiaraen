import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// CI diagnostics: register a problem matcher so compiler/KSP/test failures
// show up as annotations on the (public) run page — no workflow change needed.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    println("::add-matcher::" + rootProject.file("scripts/ci/kotlin-problem-matcher.json").absolutePath)
}

// Gradle does NOT expose local.properties as project properties (AGP only reads
// sdk.dir from it) — load it explicitly so the documented workflow
// (local.properties.example) actually takes effect. Command-line -P and
// CI-provided properties still take precedence.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun projectProp(name: String): String? =
    (project.findProperty(name) as String?)?.trim()?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }

android {
    namespace = "com.kiaraen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kiaraen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.4.0"

        // Publishable Supabase values only (anon key is public-safe behind RLS).
        buildConfigField("String", "SUPABASE_URL", "\"${projectProp("kiara.supabase.url") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${projectProp("kiara.supabase.anonKey") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            // CI injects these as -P properties from GitHub Secrets; local builds may use local.properties.
            val storeFilePath = projectProp("kiara.signing.storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = projectProp("kiara.signing.storePassword")!!
                keyAlias = projectProp("kiara.signing.keyAlias")!!
                keyPassword = projectProp("kiara.signing.keyPassword")!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (projectProp("kiara.signing.storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Robolectric UI tests render real resources (khead.png, strings, themes)
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

ksp {
    // Room schema history is exported for future migration tests (see docs/07-testing.md).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.gotrue)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    // Compose UI tests on Robolectric (NavigationSmokeTest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
