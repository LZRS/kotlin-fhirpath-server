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

import dev.ohs.fhir.fhirpath.server.services.FhirR4Service
import dev.ohs.fhir.fhirpath.server.services.FhirR4bService
import dev.ohs.fhir.fhirpath.server.services.FhirR5Service
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
          put("message", "FHIR Path API is running!")
          put(
            "endpoints",
            buildJsonObject {
              put("/health", "GET - Health check")
              put("/fhir/\$fhirpath", "POST - Evaluate R4 FHIRPath expressions")
              put("/fhir/\$fhirpath-r4b", "POST - Evaluate R4B FHIRPath expressions")
              put("/fhir/\$fhirpath-r5", "POST - Evaluate R5 FHIRPath expressions")
            },
          )
        }
      )
    }

    get("/health") {
      call.respond(mapOf("status" to "healthy", "timestamp" to Clock.System.now().toString()))
    }

    route("/fhir") {
      post("/\$fhirpath") {
        val content = call.receive<JsonObject>()
        val inputData =
          try {
            parseContentStringData(content)
          } catch (e: MissingRequiredFieldException) {
            val operationOutcome = createOperationOutcome("error", "required", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          } catch (e: IllegalStateException) {
            val operationOutcome = createOperationOutcome("error", "invalid", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          }

        val fhirpathR4Service = dependencies.resolve<FhirR4Service>()
        try {
          call.respond(HttpStatusCode.OK, fhirpathR4Service.evaluate(inputData))
        } catch (e: Exception) {
          val operationOutcome =
            createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
          call.respond(HttpStatusCode.InternalServerError, operationOutcome)
        }
      }
      post("/\$fhirpath-r4b") {
        val content = call.receive<JsonObject>()
        val inputData =
          try {
            parseContentStringData(content)
          } catch (e: MissingRequiredFieldException) {
            val operationOutcome = createOperationOutcome("error", "required", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          } catch (e: IllegalStateException) {
            val operationOutcome = createOperationOutcome("error", "invalid", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          }

        val fhirpathR4bService = dependencies.resolve<FhirR4bService>()
        try {
          call.respond(HttpStatusCode.OK, fhirpathR4bService.evaluate(inputData))
        } catch (e: Exception) {
          val operationOutcome =
            createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
          call.respond(HttpStatusCode.InternalServerError, operationOutcome)
        }
      }
      post("/\$fhirpath-r5") {
        val content = call.receive<JsonObject>()
        val inputData =
          try {
            parseContentStringData(content)
          } catch (e: MissingRequiredFieldException) {
            val operationOutcome = createOperationOutcome("error", "required", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          } catch (e: IllegalStateException) {
            val operationOutcome = createOperationOutcome("error", "invalid", e.message ?: "")
            call.respond(HttpStatusCode.BadRequest, operationOutcome)
            return@post
          }

        val fhirpathR5Service = dependencies.resolve<FhirR5Service>()
        try {
          call.respond(HttpStatusCode.OK, fhirpathR5Service.evaluate(inputData))
        } catch (e: Exception) {
          val operationOutcome =
            createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
          call.respond(HttpStatusCode.InternalServerError, operationOutcome)
        }
      }
    }
  }
}

private suspend fun parseContentStringData(contentJSObject: JsonObject): InputData =
  withContext(Dispatchers.Default) {
    if (contentJSObject["resourceType"]?.jsonPrimitive?.content != "Parameters") {
      throw IllegalStateException("Expected FHIR Parameters resource")
    }
    val inputParameters = contentJSObject["parameter"]!!.jsonArray.map { it.jsonObject }

    val contextStr =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "context" }
        ?.get("valueString")
        ?.jsonPrimitive
        ?.content
    val expressionStr =
      try {
        inputParameters
          .single { it["name"]?.jsonPrimitive?.content == "expression" }["valueString"]!!
          .jsonPrimitive
          .content
      } catch (_: NoSuchElementException) {
        throw MissingRequiredFieldException("Missing required parameter: expression")
      }
    val variables =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "variables" }
        ?.get("part")
        ?.jsonArray
        ?.associate {
          val variableJsonObject = it.jsonObject
          variableJsonObject["name"]!!.jsonPrimitive.content to
            variableJsonObject["valueString"]!!.jsonPrimitive.content
        } ?: emptyMap()
    val resource =
      try {
        inputParameters
          .single { it["name"]?.jsonPrimitive?.content == "resource" }["resource"]!!
          .jsonObject
      } catch (_: NoSuchElementException) {
        throw MissingRequiredFieldException("Missing required parameter: resource")
      }
    val terminologyServer =
      inputParameters
        .singleOrNull { it["name"]?.jsonPrimitive?.content == "terminologyserver" }
        ?.get("valueString")
        ?.jsonPrimitive
        ?.content
    InputData(contextStr, expressionStr, resource, variables, terminologyServer)
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
