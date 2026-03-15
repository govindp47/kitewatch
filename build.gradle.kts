// Root build file — no code here; all config is in modules or build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Apply ktlint to all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.4.1")
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    // Exclude build/ and generated/ dirs (KSP adapters, kspCaches) from analysis
    source.setFrom(
        fileTree("core") { exclude("**/build/**", "**/bin/**") },
        fileTree("feature") { exclude("**/build/**", "**/bin/**") },
        fileTree("infra") { exclude("**/build/**", "**/bin/**") },
        fileTree("app/src"),
        fileTree("build-logic"),
    )
}

// Install pre-commit hook
tasks.register("setup") {
    description = "Install git pre-commit hook"
    group = "setup"
    doLast {
        val hookDir = file(".git/hooks")
        val hookTarget = file("git-hooks/pre-commit")
        val hookLink = file(".git/hooks/pre-commit")
        if (!hookDir.exists()) {
            throw GradleException(".git/hooks directory not found. Run from repo root.")
        }
        hookLink.delete()
        hookLink.createNewFile()
        hookLink.writeText(hookTarget.readText())
        hookLink.setExecutable(true)
        println("pre-commit hook installed at .git/hooks/pre-commit")
    }
}
