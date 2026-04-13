plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "de.mybudgets.app"
    compileSdk = 34

    val buildVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
        ?: try {
            ProcessBuilder("git", "rev-list", "--count", "HEAD")
                .directory(rootDir)
                .start()
                .inputStream.bufferedReader().readText().trim().toInt()
        } catch (e: Exception) {
            logger.warn("Could not determine versionCode via git (${e.message}); defaulting to 1")
            1
        }

    defaultConfig {
        applicationId = "de.mybudgets.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "mybudgets"
            keyAlias = "mybudgets"
            keyPassword = "mybudgets"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Leite alle mybudgets.* System Properties vom Gradle-JVM an den Test-JVM weiter.
            // Wird benötigt, damit -Dmybudgets.live.test=true etc. im Test via System.getProperty() lesbar sind.
            System.getProperties()
                .filter { (it.key as? String)?.startsWith("mybudgets.") == true }
                .forEach { test.systemProperty(it.key as String, it.value) }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)
    implementation(libs.hbci4java)
    // Pre-compiled stub that provides java.awt.Image for jaxb-runtime's static initializer.
    // Placing it in a JAR (rather than src/main/java) avoids the Java-17 module-system
    // error "package exists in another module: java.desktop" that kapt would produce when
    // it finds a source file in the java.awt package.
    implementation(files("libs/java-awt-stub.jar"))
    implementation(libs.security.crypto)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
