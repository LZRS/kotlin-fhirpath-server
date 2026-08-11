plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.spotless)
}

// Derived from git so every build - local, CI, or Docker - carries a version traceable back to
// the release tag or commit it came from. Exact tag -> "1.2.3"; a few commits past it ->
// "1.2.3-4-gabc1234"; uncommitted changes -> "-dirty" suffix; no reachable tag (e.g. before the
// first release) -> bare abbreviated commit hash.
version = gitDescribedVersion()

application { mainClass = "io.ktor.server.netty.EngineMain" }

kotlin { jvmToolchain(21) }

ktor { fatJar { archiveFileName.set("fhirpath-server.jar") } }

// Bundled as a classpath resource (rather than a jar manifest attribute) so it survives the
// Ktor fat jar's repackaging and is readable the same way whether the app runs from
// `./gradlew run`, the plain jar, or the fat jar. See Version.kt for the runtime reader.
val generateVersionResource by
  tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version-resource")
    val versionValue = version.toString()
    outputs.dir(outputDir)
    doLast {
      outputDir.get().file("version.properties").asFile.apply {
        parentFile.mkdirs()
        writeText("version=$versionValue\n")
      }
    }
  }

sourceSets { main { resources.srcDir(generateVersionResource) } }

tasks.named("processResources") { dependsOn(generateVersionResource) }

fun gitDescribedVersion(): String =
  try {
    val process =
      ProcessBuilder("git", "describe", "--tags", "--always", "--dirty")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotBlank()) output.removePrefix("v") else "0.0.0-unknown"
  } catch (e: Exception) {
    "0.0.0-unknown"
  }

dependencies {
  implementation(libs.logback.classic)
  implementation(libs.fhir.model)
  implementation(libs.fhir.path)
  implementation(libs.ktor.server.auto.head.response)
  implementation(libs.ktor.server.config.yaml)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.double.receive)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.request.validation)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.junit.jupiter.params)
}

tasks.test { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  ratchetFrom = "origin/main"
  kotlin {
    target("**/*.kt")
    ktfmt().googleStyle()
    licenseHeaderFile("license-header.txt")
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    ktfmt().googleStyle()
  }
  flexmark {
    target("**/*.md")
    flexmark()
  }
}
