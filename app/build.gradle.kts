import java.security.MessageDigest
import java.util.zip.Adler32

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.etrisad.zenith"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.etrisad.zenith"
        minSdk = 30
        targetSdk = 36
        versionCode = 18
        versionName = "1.6.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("Boolean", "SHOW_UPDATES", "false")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("perf") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val flavor = variant.flavorName ?: ""
            val versionName = android.defaultConfig.versionName
            output.outputFileName.set("Zenith-${flavor}-${versionName}.apk")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.configureEach {
    exclude(group = "io.opencensus", module = "opencensus-api")
    exclude(group = "io.opencensus", module = "opencensus-proto")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

private class DexTrackingHostPatcher {
    private val replacements = mapOf(
        "issuetracker.google.com" to "issuetracker.exmpl.test",
        "www.google.com" to "www.exmpl.test"
    )

    fun patchOutputs(outputs: Set<File>, requireDex: Boolean) {
        var patchedFiles = 0
        outputs.forEach { output ->
            val dexFiles = when {
                output.isDirectory -> output.walkTopDown()
                    .filter { it.isFile && it.extension == "dex" }
                    .toList()
                output.isFile && output.extension == "dex" -> listOf(output)
                else -> emptyList()
            }
            dexFiles.forEach { dex ->
                patch(dex)
                patchedFiles++
                val remaining = dex.readBytes()
                replacements.keys.forEach { host ->
                    check(remaining.indexOfBytes(host.toByteArray(Charsets.US_ASCII)) < 0) {
                        "Unpatched tracking host '$host' remains in ${dex.absolutePath}"
                    }
                }
            }
        }
        check(!requireDex || patchedFiles > 0) {
            "No DEX output found to patch in ${outputs.joinToString()}"
        }
    }

    private fun patch(file: File): Boolean {
        if (!file.isFile || file.extension != "dex") return false
        val bytes = file.readBytes()
        if (bytes.size < 112) return false

        var changed = false
        replacements.forEach { (from, to) ->
            check(from.length == to.length) { "DEX replacement must keep the same length" }
            val fromBytes = from.toByteArray(Charsets.US_ASCII)
            val toBytes = to.toByteArray(Charsets.US_ASCII)
            var index = bytes.indexOfBytes(fromBytes)
            while (index >= 0) {
                toBytes.copyInto(bytes, index)
                changed = true
                index = bytes.indexOfBytes(fromBytes, index + fromBytes.size)
            }
        }
        if (!changed) return false

        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        signature.copyInto(bytes, 12)
        val adler = Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val checksum = adler.value
        bytes[8] = (checksum and 0xFF).toByte()
        bytes[9] = ((checksum ushr 8) and 0xFF).toByte()
        bytes[10] = ((checksum ushr 16) and 0xFF).toByte()
        bytes[11] = ((checksum ushr 24) and 0xFF).toByte()
        file.writeBytes(bytes)
        return true
    }

    private fun ByteArray.indexOfBytes(target: ByteArray, start: Int = 0): Int {
        if (target.isEmpty() || target.size > size) return -1
        outer@ for (i in start..size - target.size) {
            for (j in target.indices) {
                if (this[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

tasks.configureEach {
    val producesFinalDex = (name.startsWith("merge") && name.contains("Dex")) ||
        (name.startsWith("minify") && name.contains("WithR8"))
    if (producesFinalDex) {
        val requiresDex = name.contains("WithR8")
        doLast {
            DexTrackingHostPatcher().patchOutputs(outputs.files.files, requiresDex)
        }
    }
}
