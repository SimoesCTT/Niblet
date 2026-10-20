plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {    }

    namespace = "com.niblet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.niblet.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil:2.6.0")
    implementation("org.tukaani:xz:1.10")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-nop:1.7.36")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")
    implementation("com.github.junrar:junrar:7.5.5")
}
