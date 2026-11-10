import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localSecrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val navigationApiKey = localSecrets.getProperty("GOOGLE_NAVIGATION_API_KEY", "")
    .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "app.routewise"
    compileSdk = 35
    defaultConfig {
        applicationId = if (providers.gradleProperty("isolatedTests").isPresent) "app.routewise.qa" else "app.routewise"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-dev"
        buildConfigField("String", "GOOGLE_NAVIGATION_API_KEY", "\"$navigationApiKey\"")
        manifestPlaceholders["googleNavigationApiKey"] = navigationApiKey
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach {
    dependsOn("kspDebugKotlin")
}

tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doFirst {
        check(providers.gradleProperty("isolatedTests").isPresent) {
            "Connected tests must use -PisolatedTests to protect the main RouteWise app data."
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.icons)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.serialization)
    implementation(libs.hilt.android)
    implementation(libs.navigation)
    implementation(libs.reorderable)
    implementation(libs.okhttp)
    implementation(libs.maplibre)
    implementation(libs.turf)
    implementation(libs.google.navigation)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}
