plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktor)
  alias(libs.plugins.spotless)
}

version = "1.0.0"

application { mainClass = "io.ktor.server.netty.EngineMain" }

kotlin { jvmToolchain(21) }

ktor { fatJar { archiveFileName.set("fhirpath-server.jar") } }

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
