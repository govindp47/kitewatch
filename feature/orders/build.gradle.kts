plugins {
    alias(libs.plugins.kitewatch.android.library)
    alias(libs.plugins.kitewatch.android.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kitewatch.feature.orders"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
}
