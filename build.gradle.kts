val kotlin_version: String by project
val logback_version: String by project
val fhir_path_version: String by project

plugins {
  kotlin("jvm") version "2.3.0"
  id("io.ktor.plugin") version "3.4.2"
  id("com.diffplug.spotless") version "7.0.1"
}

version = "0.0.1"

application { mainClass = "io.ktor.server.netty.EngineMain" }

kotlin { jvmToolchain(21) }

ktor { fatJar { archiveFileName.set("fhirpath-server.jar") } }

dependencies {
  implementation("ch.qos.logback:logback-classic:${logback_version}")
  implementation("dev.ohs.fhir:fhir-model-jvm:1.0.0-beta02")
  implementation("dev.ohs.fhir:fhir-path-jvm:${fhir_path_version}")
  implementation("io.ktor:ktor-server-auto-head-response")
  implementation("io.ktor:ktor-server-config-yaml")
  implementation("io.ktor:ktor-server-core")
  implementation("io.ktor:ktor-server-cors")
  implementation("io.ktor:ktor-server-di")
  implementation("io.ktor:ktor-server-double-receive")
  implementation("io.ktor:ktor-server-netty")
  implementation("io.ktor:ktor-server-request-validation")
  implementation("io.ktor:ktor-server-content-negotiation")
  implementation("io.ktor:ktor-serialization-kotlinx-json")
  testImplementation("io.ktor:ktor-server-test-host")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

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
