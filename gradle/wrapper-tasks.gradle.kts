import java.util.Properties
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.util.GradleVersion

// Keep Gradle's generator away from the root Java-selection dispatchers.
fun Wrapper.launcher(name: String, jarName: String) {
    scriptFile = file("gradle/launchers/gradlew-$name")
    jarFile = file("gradle/wrapper/$jarName.jar")
    val configuration = Properties().apply {
        file("gradle/wrapper/$jarName.properties").inputStream().use { load(it) }
    }
    distributionUrl = configuration.getProperty("distributionUrl")
    distributionSha256Sum = configuration.getProperty("distributionSha256Sum")

    doFirst {
        check(GradleVersion.current() >= GradleVersion.version("9.0")) {
            "Regenerate wrappers using Java 17 or newer and Gradle 9. " +
                "Building the project still supports Java 11."
        }
    }
}

tasks.named<Wrapper>("wrapper") {
    launcher("modern", "gradle-wrapper")
}

tasks.register<Wrapper>("legacyWrapper") {
    group = "build setup"
    description = "Regenerates the stock launcher for Java 11–16."
    launcher("legacy", "gradle-wrapper-legacy")
}

tasks.register("updateWrappers") {
    group = "build setup"
    description = "Regenerates both stock launchers."
    dependsOn("wrapper", "legacyWrapper")
}
