import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

//val properties = Properties()
//val localPropertiesFile: File = rootProject.file("local.properties")
//if (localPropertiesFile.exists()) {
//    localPropertiesFile.inputStream().use { properties.load(it) }
//}

fun getVersionCode(): Int {
    return try {
        val byteOut = ByteArrayOutputStream()
        project.exec {
            commandLine = "git rev-list --count origin/lampa-2.0".split(" ")
            standardOutput = byteOut
        }
        byteOut.toString("UTF-8").trim().toInt()
    } catch (_: Exception) {
        // ignore
        1
    }
}

fun getVersionName(): String {
    return try {
        val byteOut = ByteArrayOutputStream()
        project.exec {
            commandLine = "git describe --tags --dirty".split(" ")
            standardOutput = byteOut
        }
        byteOut.toString("UTF-8").trim()
    } catch (_: Exception) {
        // ignore
        "0.0.0"
    }
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "top.rootu.lampa"
        minSdk = 16
        targetSdk = 28
        versionCode = getVersionCode()
        versionName = getVersionName()
        multiDexEnabled = true
        // Use SupportLibrary for vectors
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val keystoreProperties = Properties()
            val keystorePropsFile: File = rootProject.file("keystore/keystore_config")

            if (keystorePropsFile.exists()) {
                file("keystore/keystore_config").inputStream().use { keystoreProperties.load(it) }
                storeFile = file("$keystoreProperties.storeFile")
                storePassword = "$keystoreProperties.storePassword"
                keyAlias = "$keystoreProperties.keyAlias"
                keyPassword = "$keystoreProperties.keyPassword"
            } else {
                storeFile = keystoreProperties.getProperty("storeFile", System.getenv("KEYSTORE_FILE"))?.let { rootProject.file(it) }
                storePassword = keystoreProperties.getProperty("storePassword", System.getenv("KEYSTORE_PASSWORD"))
                keyAlias = keystoreProperties.getProperty("keyAlias", System.getenv("RELEASE_SIGN_KEY_ALIAS"))
                keyPassword = keystoreProperties.getProperty("keyPassword", System.getenv("RELEASE_SIGN_KEY_PASSWORD"))
            }
        }
    }
    lint {
        // Turns off checks for the issue IDs you specify.
        disable += "GradleDependency"
        // To enable checks for only a subset of issue IDs and ignore all others,
        // list the issue IDs with the 'check' property instead. This property overrides
        // any issue IDs you enable or disable using the properties above.
        checkOnly += "NewApi" + "InlinedApi"
        // If set to true, turns off analysis progress reporting by lint.
        quiet = true
        // If set to true (default), stops the build if errors are found.
        abortOnError = false
        // If set to true, lint only reports errors.
        ignoreWarnings = true
        // If set to true, lint also checks all dependencies as part of its analysis.
        // Recommended for projects consisting of an app with library dependencies.
        checkDependencies = true
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { output ->
                val flavour = variant.flavorName
                //val builtType = variant.buildType.name
                val versionName = variant.versionName
                val arch = output.filters.first().identifier
                output.outputFileName =
                    "lampa-${flavour}-${versionName}(${arch}).apk"
            }
    }
//    testOptions {
//        unitTests {
//            isIncludeAndroidResources = true
//        }
//    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    compileOptions {
        // For AGP 4.1+
        isCoreLibraryDesugaringEnabled = true
//        sourceCompatibility to JavaVersion.VERSION_1_8
//        targetCompatibility to JavaVersion.VERSION_1_8
    }
    flavorDimensions += listOf("gecko")
    productFlavors {
        create("gecko") {
            dimension = "gecko"
        }
    }
    buildFeatures {
        viewBinding = true
    }

    namespace = "top.rootu.lampa"
}

dependencies {
    // For AGP 7.4+
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    // https://maven.mozilla.org/?prefix=maven2/org/mozilla/geckoview/
    val geckoViewVersion = "118.0.20230918143747"
    implementation("org.mozilla.geckoview:geckoview:$geckoViewVersion") {
        exclude(null, "snakeyaml") // disable non-compatible yaml library: https://github.com/mozilla/geckoview/issues/139
    }
    implementation("org.yaml:snakeyaml:1.26:android") // use updated yaml library compatible with old Android
    // JavaScript to run PAC file to get correct proxies
    implementation("org.mozilla:rhino:1.7.14")
    // okhttp for android 4.1+ // 3.12.12 - latest version for API 16
    //noinspection GradleDependency
    implementation("com.squareup.okhttp3:okhttp:3.12.12")
    //noinspection GradleDependency
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:3.12.12")
    // multidex
    implementation("androidx.multidex:multidex:2.0.1")
    // netcipher
    implementation("info.guardianproject.netcipher:netcipher:2.1.0")
    // androidx
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("org.brotli:dec:0.1.2")
    // material-ui
    implementation("com.google.android.material:material:1.9.0")
    // speech
    implementation("net.gotev:speech:1.6.2")
    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // json serializer
    //noinspection GradleDependency 2.10.x thorow java.lang.VerifyError on api17
    implementation("com.google.code.gson:gson:2.9.1") // 2.10.1
    // tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}