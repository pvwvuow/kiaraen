import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import org.gradle.api.provider.Property

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
        versionCode = 9
        versionName = "0.6.0"

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

// ── kiara web UI ────────────────────────────────────────────────────────────
// The approved design, exported as ONE self-contained file (web/kiara-app.html),
// IS the app UI. At build time it is unpacked into assets/web/: every inline
// base64 image/font becomes a real file (deduplicated, decoded lazily by the
// WebView instead of parsing a 16 MB page), the hooks in web/patches.json are
// applied and the shell files (kiara-shell.css, kiara-fit.js, kiara-live.js)
// are added. No web/kiara-app.html → nothing is packed and the native Compose
// UI runs as before. Preview the same output: python3 scripts/web/build_web.py
abstract class PrepareKiaraWeb : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webDir: DirectoryProperty

    @get:Input
    abstract val supabaseUrl: Property<String>

    @get:Input
    abstract val supabaseAnon: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val src = webDir.get().asFile
        val page = src.resolve("kiara-app.html")
        if (!page.isFile) {
            logger.lifecycle("kiara web: web/kiara-app.html not found, the native UI is used")
            return
        }
        val dest = out.resolve("web")
        dest.resolve("m").mkdirs()
        var html = page.readText(Charsets.UTF_8).replace("\r\n", "\n")

        val patchFile = src.resolve("patches.json")
        if (patchFile.isFile) {
            @Suppress("UNCHECKED_CAST")
            val patches = groovy.json.JsonSlurper().parse(patchFile) as List<Map<String, Any?>>
            for (p in patches) {
                val name = p["name"]?.toString() ?: "?"
                val find = p["find"]?.toString() ?: continue
                val replace = p["replace"]?.toString() ?: ""
                val at = html.indexOf(find)
                if (at < 0) {
                    logger.warn("kiara web: hook '$name' not found in kiara-app.html (skipped)")
                    continue
                }
                html = html.substring(0, at) + replace + html.substring(at + find.length)
            }
        }

        val types = mapOf(
            "image/png" to "png", "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/webp" to "webp",
            "image/gif" to "gif", "image/svg+xml" to "svg", "font/woff2" to "woff2", "font/woff" to "woff",
            "font/ttf" to "ttf", "font/otf" to "otf", "application/font-woff2" to "woff2",
            "application/x-font-ttf" to "ttf",
        )
        val names = HashMap<String, String>()
        val sha1 = MessageDigest.getInstance("SHA-1")
        val dataUri = Regex("data:([a-zA-Z0-9.+/-]+);base64,([A-Za-z0-9+/=]+)")
        html = dataUri.replace(html) { m ->
            val ext = types[m.groupValues[1].lowercase()]
            if (ext == null) {
                m.value
            } else {
                val b64 = m.groupValues[2]
                names.getOrPut(b64) {
                    val bytes = Base64.getMimeDecoder().decode(b64)
                    val id = sha1.digest(bytes).joinToString("") { b -> "%02x".format(b) }.take(16)
                    val file = "m/$id.$ext"
                    dest.resolve(file).writeBytes(bytes)
                    file
                }
            }
        }

        val head = buildString {
            append("<script>")
            if (supabaseUrl.get().isNotBlank() && supabaseAnon.get().isNotBlank()) {
                val esc: (String) -> String = { it.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"") }
                append("window.KIARA_CFG={url:\"").append(esc(supabaseUrl.get()))
                append("\",anon:\"").append(esc(supabaseAnon.get())).append("\"};")
            }
            append("</script>\n")
            // dark theme class before first paint (no flash)
            append("<script>try{var d=localStorage.getItem('kiara.dark');")
            append("if(d!=='0'&&d!=='1')d=matchMedia('(prefers-color-scheme: dark)').matches?'1':'0';")
            append("if(d==='1')document.documentElement.classList.add('kdark')}catch(e){}</script>\n")
            append("<link rel=\"stylesheet\" href=\"kiara-shell.css\">\n")
            append("<link rel=\"stylesheet\" href=\"kiara-dark.css\">\n")
            append("<script src=\"kiara-fit.js\"></script>\n")
        }
        html = html.replaceFirst("</head>", head + "</head>")
        val tail = "<script src=\"kiara-auth.js\"></script>\n<script src=\"kiara-live.js\"></script>\n"
        val end = html.lastIndexOf("</body>")
        html = if (end >= 0) html.substring(0, end) + tail + html.substring(end) else html + tail
        dest.resolve("index.html").writeText(html, Charsets.UTF_8)
        for (n in listOf("kiara-shell.css", "kiara-dark.css", "kiara-fit.js", "kiara-auth.js", "kiara-live.js")) {
            val f = src.resolve(n)
            if (f.isFile) f.copyTo(dest.resolve(n), overwrite = true)
        }
        logger.lifecycle("kiara web: ${html.length / 1024} KB page, ${names.size} media files")
    }
}

androidComponents {
    onVariants { variant ->
        val prep = tasks.register<PrepareKiaraWeb>(
            "prepareKiaraWeb" + variant.name.replaceFirstChar { it.uppercase() },
        ) {
            webDir.set(rootProject.layout.projectDirectory.dir("web"))
            supabaseUrl.set(projectProp("kiara.supabase.url") ?: "")
            supabaseAnon.set(projectProp("kiara.supabase.anonKey") ?: "")
        }
        variant.sources.assets?.addGeneratedSourceDirectory(prep, PrepareKiaraWeb::outputDir)
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
