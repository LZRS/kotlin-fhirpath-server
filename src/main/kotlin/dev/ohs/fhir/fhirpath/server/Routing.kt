/*
 * Copyright 2025-2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.fhirpath.server

import dev.ohs.fhir.fhirpath.server.services.FhirPathR4BService
import dev.ohs.fhir.fhirpath.server.services.FhirPathR4Service
import dev.ohs.fhir.fhirpath.server.services.FhirPathR5Service
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun Application.configureRouting() {
  install(AutoHeadResponse)
  install(DoubleReceive)

  routing {
    get("/") {
      call.respond(
        buildJsonObject {
          put("message", "Kotlin FHIRPath server is running!")
          put(
            "endpoints",
            buildJsonObject {
              put("/health", "GET - Health check")
              put("/fhirpath-r4", "POST - Evaluate R4 FHIRPath expressions")
              put("/fhirpath-r4b", "POST - Evaluate R4B FHIRPath expressions")
              put("/fhirpath-r5", "POST - Evaluate R5 FHIRPath expressions")
            },
          )
        }
      )
    }

    get("/health") {
      call.respond(mapOf("status" to "healthy", "timestamp" to Clock.System.now().toString()))
    }

    post("/fhirpath-r4") { parseAndEvaluateFhirPath(call) { FhirPathR4Service().evaluate(it) } }
    post("/fhirpath-r4b") { parseAndEvaluateFhirPath(call) { FhirPathR4BService().evaluate(it) } }
    post("/fhirpath-r5") { parseAndEvaluateFhirPath(call) { FhirPathR5Service().evaluate(it) } }
  }
}

private suspend fun parseAndEvaluateFhirPath(routingCall: RoutingCall, evaluate: suspend (InputData) -> JsonElement) {
  val content = routingCall.receive<JsonObject>()
  val inputData =
    try {
      parseContentStringData(content)
    } catch (e: MissingRequiredFieldException) {
      routingCall.respond(
        HttpStatusCode.BadRequest,
        createOperationOutcome("error", "required", e.message ?: ""),
      )
      return
    } catch (e: IllegalStateException) {
      routingCall.respond(
        HttpStatusCode.BadRequest,
        createOperationOutcome("error", "invalid", e.message ?: ""),
      )
      return
    }

  try {
    routingCall.respond(HttpStatusCode.OK, evaluate(inputData))
  } catch (e: Exception) {
    routingCall.respond(
      HttpStatusCode.InternalServerError,
      createOperationOutcome("error", "exception", "Internal server error: ${e.message}"),
    )
  }
}

/**
 * Parses a FHIR [Parameters](https://www.hl7.org/fhir/parameters.html) resource into [InputData].
 *
 * Required parameters: `expression`, `resource`.
 * Optional parameters: `context`, `variables`, `terminologyserver`.
 *
 * Example input:
 * ```json
 * {
 *   "resourceType": "Parameters",
 *   "parameter": [
 *     { "name": "expression", "valueString": "Patient.name.given" },
 *     { "name": "resource", "resource": { "resourceType": "Patient", "name": [{ "given": ["John"] }] } },
 *     { "name": "context", "valueString": "Patient" },
 *     { "name": "terminologyserver", "valueString": "https://tx.fhir.org/r4" },
 *     {
 *       "name": "variables",
 *       "part": [
 *         { "name": "myVar", "valueString": "someValue" }
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * @throws MissingRequiredFieldException if `parameter`, `expression`, or `resource` is absent.
 * @throws IllegalStateException if `resourceType` is not `"Parameters"`.
 */
private suspend fun parseContentStringData(contentJSObject: JsonObject): InputData =
  withContext(Dispatchers.Default) {
    if (contentJSObject["resourceType"]?.jsonPrimitive?.content != "Parameters") {
      throw IllegalStateException("Expected FHIR Parameters resource")
    }
    val inputParameters = contentJSObject["parameter"]?.jsonArray?.map { it.jsonObject } ?: throw MissingRequiredFieldException("Missing required field: 'parameter'")

    val contextStr =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "context" }
        ?.get("valueString")
        ?.jsonPrimitive
        ?.content
    val expressionStr = inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "expression" }?.get("valueString")
      ?.jsonPrimitive
      ?.content ?: throw MissingRequiredFieldException("Missing required parameter: 'expression'")
    val variables =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "variables" }
        ?.get("part")
        ?.jsonArray
        ?.associate {
          val variableJsonObject = it.jsonObject
          val variableName = variableJsonObject["name"]?.jsonPrimitive?.content ?: throw MissingRequiredFieldException("Missing required parameter: 'part.name'")
          variableName to
            variableJsonObject["valueString"]?.jsonPrimitive?.content
        } ?: emptyMap()
    val resourceString = inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "resource" }?.get("resource")
      ?.jsonObject
      ?.toString() ?: throw MissingRequiredFieldException("Missing required parameter: 'resource'")

    val terminologyServer =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "terminologyserver" }
        ?.get("valueString")
        ?.jsonPrimitive
        ?.content
    InputData(contextStr, expressionStr, resourceString, variables, terminologyServer)
  }

private fun createOperationOutcome(severity: String, code: String, message: String): JsonObject {
  return buildJsonObject {
    put("resourceType", "OperationOutcome")
    put(
      "issue",
      buildJsonArray {
        add(
          buildJsonObject {
            put("severity", severity)
            put("code", code)
            put("details", buildJsonObject { put("text", message) })
          }
        )
      },
    )
  }
}
