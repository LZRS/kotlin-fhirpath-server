package dev.ohs.dev.ohs.fhir.fhirpath.server.services

import dev.ohs.dev.ohs.fhir.fhirpath.server.InputData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface FhirpathService {
    suspend fun evaluate(inputData: InputData): JsonElement
}