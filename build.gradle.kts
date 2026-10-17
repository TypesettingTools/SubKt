import java.net.URI
import org.jetbrains.dokka.gradle.formats.DokkaFormatPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.4.21"
    `java-gradle-plugin`
    id("org.jetbrains.dokka") version "2.2.0"
}

gradlePlugin {
    plugins {
        create("subPlugin") {
            id = "myaa.subkt"
            implementationClass = "myaa.subkt.tasks.SubPlugin"
        }
    }
}

group = "myaa"
version = "0.1.22"

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java {
            srcDirs(listOf(
                    "ttorrent/bencoding/src/main/java",
                    "ttorrent/common/src/main/java"
            ))
        }
    }

    register("samples") {
        compileClasspath += sourceSets["main"].compileClasspath
    }
}

dependencies {
    // Use the host Gradle's Kotlin runtime, including 2.0.21 on Gradle 8.14.
    implementation(kotlin("stdlib", embeddedKotlinVersion))
    implementation(kotlin("reflect", embeddedKotlinVersion))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.apache.velocity:velocity-engine-core:2.2")
    implementation("org.apache.pdfbox:fontbox:2.0.21")

    implementation("com.google.code.gson:gson:2.8.6")
    implementation("io.ktor:ktor-client-cio:1.4.0")
    implementation("io.ktor:ktor-client-gson:1.4.0")
    implementation("io.ktor:ktor-client-auth-jvm:1.4.0")
    implementation("commons-net:commons-net:3.6")
    implementation("commons-io:commons-io:2.7")
    implementation("com.jcraft:jsch:0.1.55")
    // ttorrent dependencies
    implementation("org.slf4j:slf4j-log4j12:1.6.4")
    implementation("org.jetbrains:annotations:16.0.2")
    implementation("commons-codec:commons-codec:1.14")

    "samplesImplementation"(sourceSets["main"].output)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit", embeddedKotlinVersion))
}

// Keep the plugin usable on Java 11–16 with Gradle 8.14 as well as modern Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Gradle 8.14's Kotlin DSL compiler must be able to read our metadata.
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

// Dokka v2 ships HTML/Javadoc formats; retain the existing Markdown output too.
abstract class DokkaGfmPlugin : DokkaFormatPlugin("gfm")
apply<DokkaGfmPlugin>()

dependencies {
    add("dokkaGfmPlugin", "org.jetbrains.dokka:gfm-plugin:2.2.0")
}

dokka {
    dokkaPublications.named("gfm") {
        outputDirectory.set(layout.buildDirectory.dir("dokka/gfm"))
    }
    dokkaSourceSets.named("main") {
        jdkVersion.set(11)
        includes.from("src/docs/markdown/packages.md")
        samples.from("src/samples/kotlin/tasks.kt")
        externalDocumentationLinks.register("gradle") {
            url.set(URI("https://docs.gradle.org/current/javadoc/"))
            packageListUrl.set(URI("https://docs.gradle.org/current/javadoc/element-list"))
        }
        externalDocumentationLinks.register("velocity") {
            url.set(URI("https://velocity.apache.org/engine/2.2/apidocs/"))
        }
        perPackageOption {
            matchingRegex.set("com\\.turn\\.ttorrent.*")
            suppress.set(true)
        }
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URI("https://github.com/Myaamori/SubKt/blob/$version/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}

tasks.register("dokka") {
    group = "documentation"
    description = "Generates Markdown API documentation."
    dependsOn("dokkaGeneratePublicationGfm")
}
