package dev.ohs.dev.ohs.fhir.fhirpath.server

import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR4Service
import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR4bService
import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR5Service
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.configureFrameworks() {
    dependencies {
        provide<FhirR4Service> {
            FhirR4Service()
        }
        provide<FhirR4bService> {
            FhirR4bService()
        }
        provide<FhirR5Service> {
            FhirR5Service()
        }
    }
}
