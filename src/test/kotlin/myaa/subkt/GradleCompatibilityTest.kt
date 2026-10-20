package myaa.subkt

import myaa.subkt.ass.ASSFile
import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleCompatibilityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun project(script: String): File = temporaryFolder.newFolder().apply {
        resolve("settings.gradle.kts").writeText("""
            rootProject.name = "compatibility-test"
            check(JavaVersion.current().majorVersion == "${System.getProperty("java.specification.version")}") {
                "The consumer build must run on the same JDK as the compatibility tests"
            }
        """.trimIndent())
        resolve("gradle.properties").writer().use { writer ->
            Properties().apply {
                setProperty("org.gradle.java.home", System.getProperty("java.home"))
            }.store(writer, "Keep the consumer on the JDK being tested")
        }
        resolve("build.gradle.kts").writeText("""
            import myaa.subkt.tasks.*
            plugins { id("myaa.subkt") }
            $script
        """.trimIndent())
    }

    private fun run(project: File, vararg tasks: String) = GradleRunner.create()
        .withProjectDir(project)
        .withPluginClasspath()
        .withArguments(*tasks, "--stacktrace", "--console=plain")
        .build()

    @Test
    fun `subtitle task chain and torrent execute on the current JDK`() {
        val project = project("""
            subs {
                episodes("01")
                release("TV")
                merge {
                    from("dialogue.ass") { incrementLayer(10) }
                }
                swap { from(merge.item()) }
                chapters { from(merge.item()) }
                torrent {
                    from(swap.item())
                    trackers("https://tracker.invalid/announce")
                }
            }
        """.trimIndent())
        project.resolve("dialogue.ass").writeText("""
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1920
            PlayResY: 1080

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Karen{**-chan}
            Comment: 0,0:00:05.00,0:00:06.00,Default,chptr,0,0,0,,Part A
        """.trimIndent())

        val result = run(project, "torrent.01", "chapters.01")
        for (task in listOf("merge.01", "swap.01", "chapters.01", "torrent.01")) {
            assertEquals(TaskOutcome.SUCCESS, result.task(":$task.TV")?.outcome, result.output)
        }
        val swapped = ASSFile(project.resolve("build/swap.01.TV.ass"))
        assertEquals(10, swapped.events.lines.first().layer)
        assertEquals("Karen{*}-chan{*}", swapped.events.lines.first().text)
        assertTrue(project.resolve("build/chapters.01.TV.txt").readText().contains("CHAPTER02=00:00:05.000"))
        assertTrue(project.resolve("build/torrent.01.TV.torrent").length() > 0)

        val rerun = run(project, "torrent.01", "chapters.01")
        assertEquals(TaskOutcome.UP_TO_DATE, rerun.task(":merge.01.TV")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, rerun.task(":swap.01.TV")?.outcome)
    }

    @Test
    fun `HTTP task runs with the Gradle Kotlin runtime`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/test") { exchange ->
            val response = "{\"ok\":true}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val project = project("""
                val request by tasks.registering(HTTP::class) {
                    host("127.0.0.1")
                    https(false)
                    port(${server.address.port})
                    endpoint("/test")
                }
                tasks.register("verifyResponse") {
                    dependsOn(request)
                    doLast { check(request.get().responseJson.asJsonObject["ok"].asBoolean) }
                }
            """.trimIndent())
            assertEquals(TaskOutcome.SUCCESS, run(project, "verifyResponse").task(":request")?.outcome)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `automation executes a subprocess through Gradle services`() {
        assumeTrue("The fake Aegisub executable requires a POSIX shell", File("/bin/sh").exists())
        val project = project("""
            tasks.register<Automation>("automate") {
                aegisubCli(file("fake-aegisub").absolutePath)
                from("input.ass")
                out("output.ass")
                macro("test macro")
            }
        """.trimIndent())
        // This fixture substitutes for Aegisub so the test needs no external installation.
        project.resolve("fake-aegisub").apply {
            writeText("#!/bin/sh\n[ \"${'$'}3\" = \"test macro\" ] || exit 1\ncp \"${'$'}1\" \"${'$'}2\"\n")
            setExecutable(true)
        }
        project.resolve("input.ass").writeText("test subtitle")
        assertEquals(TaskOutcome.SUCCESS, run(project, "automate").task(":automate")?.outcome)
        assertEquals("test subtitle", project.resolve("output.ass").readText())
    }
}
