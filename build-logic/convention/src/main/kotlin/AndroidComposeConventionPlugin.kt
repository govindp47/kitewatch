import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies Compose build features and the Kotlin Compose compiler plugin to any
 * Android module (application or library). Apply AFTER the Android plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure(ApplicationExtension::class.java) {
                    buildFeatures.compose = true
                }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure(LibraryExtension::class.java) {
                    buildFeatures.compose = true
                }
            }
        }
    }
}
