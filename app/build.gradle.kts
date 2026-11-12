import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Hidden-menu password folder: the Drive folder id lives ONLY in the
// developer's local.properties (gitignored, never pushed) — it must never
// appear in the repo, or anyone could find the password location. A build
// without local.properties (e.g. CI) gets an empty id, so the hidden menu
// simply cannot fetch a password there (cached name / NO_NETWORK fallback);
// the local build gets the real id.
val hiddenMenuFolderId: String = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    props.getProperty("hiddenMenuFolderId", "")
}

android {
    namespace = "com.nezammonitor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nezammonitor.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 72
        versionName = "3.8.7"
        buildConfigField("String", "HIDDEN_MENU_FOLDER_ID", "\"$hiddenMenuFolderId\"")
    }

    buildToolsVersion = "34.0.0"

    // The release keystore lives only on the developer's machine — it is NOT in
    // the repo (and must never be committed). The CI build therefore has no
    // keystore file: without this guard the whole build failed at configuration
    // time ("keystore file not found"), even for assembleDebug, because the
    // debug build type also used the release signing config. When the file is
    // missing, fall back to the default debug keystore / unsigned release.
    val releaseKeystoreFile = rootProject.file("release.keystore")
    val hasReleaseKeystore = releaseKeystoreFile.exists()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = "nezam1234"
                keyAlias = "nezamrelease"
                keyPassword = "nezam1234"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // HTTP client for API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Apache POI for Word document manipulation
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// B Nazanin fonts: the TTFs are binary, and the repo's push channel only
// carries text, so the fonts live in the repo as base64 (fonts-src/*.b64)
// and this task decodes them into src/main/assets/fonts/ before the build.
// The decoded *.ttf files are gitignored and never committed.
val decodeFonts by tasks.registering {
    val srcDir = layout.projectDirectory.dir("fonts-src")
    val outDir = layout.projectDirectory.dir("src/main/assets/fonts")
    inputs.dir(srcDir)
    outputs.dir(outDir)
    doLast {
        outDir.asFile.mkdirs()
        srcDir.asFile.listFiles { f -> f.name.endsWith(".b64") }.orEmpty().forEach { b64 ->
            val out = File(outDir.asFile, b64.name.removeSuffix(".b64"))
            if (!out.exists() || out.lastModified() < b64.lastModified()) {
                out.writeBytes(Base64.getMimeDecoder().decode(b64.readText()))
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(decodeFonts) }

// Launcher icon variants (v3.8.5): the variant PNGs are generated at build
// time from the original icon (same hue-shift as the reference script in
// ~/workspace/icon-variants/make_variants.py) because the repo's push
// channel only carries text — binary PNGs can never be committed. The
// generated files are gitignored and never committed.
val generateLauncherIconVariants by tasks.registering {
    val src = layout.projectDirectory.file("src/main/res/mipmap-hdpi/ic_launcher.png")
    val outDir = layout.projectDirectory.dir("src/main/res/mipmap-hdpi")
    inputs.file(src)
    outputs.file(outDir.file("ic_launcher_green.png"))
    outputs.file(outDir.file("ic_launcher_gold.png"))
    outputs.file(outDir.file("ic_launcher_dark.png"))
    doLast {
        val img = ImageIO.read(src.asFile)
        val w = img.width
        val h = img.height
        val pixels = IntArray(w * h)
        img.getRGB(0, 0, w, h, pixels, 0, w)
        val hsb = FloatArray(3)
        fun variant(transform: (Float, Float, Float) -> Triple<Float, Float, Float>): BufferedImage {
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val outPixels = IntArray(pixels.size)
            for (i in pixels.indices) {
                val argb = pixels[i]
                Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsb)
                val (nh, ns, nb) = transform(hsb[0], hsb[1], hsb[2])
                val rgb = Color.HSBtoRGB(nh, ns, nb) and 0x00FFFFFF
                outPixels[i] = ((argb ushr 24) shl 24) or rgb
            }
            out.setRGB(0, 0, w, h, outPixels, 0, w)
            return out
        }
        val isBlue: (Float, Float, Float) -> Boolean = { hh, ss, bb ->
            hh >= 190f / 360f && hh <= 260f / 360f && ss > 40f / 255f && bb > 30f / 255f
        }
        val green = variant { hh, ss, bb ->
            if (isBlue(hh, ss, bb)) Triple((hh - 70f / 360f + 1f) % 1f, ss, bb) else Triple(hh, ss, bb)
        }
        ImageIO.write(green, "png", File(outDir.asFile, "ic_launcher_green.png"))
        val gold = variant { hh, ss, bb ->
            if (isBlue(hh, ss, bb)) Triple(35f / 360f, ss, bb) else Triple(hh, ss, bb)
        }
        ImageIO.write(gold, "png", File(outDir.asFile, "ic_launcher_gold.png"))
        val dark = variant { hh, ss, bb -> Triple(hh, ss * 0.92f, bb * 0.70f) }
        ImageIO.write(dark, "png", File(outDir.asFile, "ic_launcher_dark.png"))
    }
}
tasks.named("preBuild").configure { dependsOn(generateLauncherIconVariants) }
