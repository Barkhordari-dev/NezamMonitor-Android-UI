import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nezammonitor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nezammonitor.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 66
        versionName = "3.8.1"
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
