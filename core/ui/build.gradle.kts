plugins {
    alias(libs.plugins.kitewatch.android.library)
    alias(libs.plugins.kitewatch.android.compose)
}

android {
    namespace = "com.kitewatch.ui"
}

dependencies {
    implementation(project(":core:domain"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.paging.compose)
}
