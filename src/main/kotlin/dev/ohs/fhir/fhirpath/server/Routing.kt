package dev.ohs.dev.ohs.fhir.fhirpath.server

import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR4Service
import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR4bService
import dev.ohs.dev.ohs.fhir.fhirpath.server.services.FhirR5Service
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(DoubleReceive)

    routing {
        get("/") {
            call.respond(mapOf(
                "message" to "FHIR Path API is running!",
                "endpoints" to mapOf(
                    "/health" to "GET - Health check",
                    "/fhir/fhirpath" to "POST - Evaluate R4 FHIRPath expressions",
                    "/fhir/fhirpath-r4b" to "POST - Evaluate R4B FHIRPath expressions",
                    "/fhir/fhirpath-r5" to "POST - Evaluate R5 FHIRPath expressions",
                )
            ))
        }

        get("/health") {
            call.respond(mapOf(
                "status" to "healthy",
                "timestamp" to Clock.System.now().toString(),
            ))
        }

        route("/fhir") {
            post("/\$fhirpath") {
                val content = call.receive<JsonObject>()
                val inputData = try {
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
                    val operationOutcome = createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, operationOutcome)
                }

            }
            post("/\$fhirpath-r4b") {
                val content = call.receive<JsonObject>()
                val inputData = try {
                    parseContentStringData(content)
                } catch (e: MissingRequiredFieldException){
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
                    val operationOutcome = createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, operationOutcome)
                }
            }
            post("/\$fhirpath-r5") {
                val content = call.receive<JsonObject>()
                val inputData = try {
                    parseContentStringData(content)
                } catch (e: MissingRequiredFieldException){
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
                } catch (e: Exception){
                    val operationOutcome = createOperationOutcome("error", "exception", "Internal server error: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, operationOutcome)
                }
            }
        }
    }
}

private suspend fun parseContentStringData(contentJSObject: JsonObject): InputData = withContext(Dispatchers.Default) {
    if (contentJSObject["resourceType"]?.jsonPrimitive?.content != "Parameters"){
        throw IllegalStateException("Expected FHIR Parameters resource")
    }
    val inputParameters = contentJSObject["parameter"]!!.jsonArray.map { it.jsonObject }

    val contextStr = inputParameters.singleOrNull { it["name"]?.jsonPrimitive?.content == "context" }?.get("valueString")?.jsonPrimitive?.content
    val expressionStr = try {
        inputParameters.single { it["name"]?.jsonPrimitive?.content == "expression" }["valueString"]!!.jsonPrimitive.content
    } catch (_: NoSuchElementException) {
        throw MissingRequiredFieldException("Missing required parameter: expression")
    }
    val variables =
        inputParameters.singleOrNull { it["name"]?.jsonPrimitive?.content == "variables" }
            ?.get("part")?.jsonArray?.associate {
                val variableJsonObject = it.jsonObject
                variableJsonObject["name"]!!.jsonPrimitive.content to variableJsonObject["valueString"]!!.jsonPrimitive.content
            } ?: emptyMap()
    val resource = try {
        inputParameters.single { it["name"]?.jsonPrimitive?.content == "resource" }["resource"]!!.jsonObject
    } catch (_: NoSuchElementException) {
        throw MissingRequiredFieldException("Missing required parameter: resource")
    }
    val terminologyServer = inputParameters.singleOrNull { it["name"]?.jsonPrimitive?.content  == "terminologyserver" }?.get("valueString")?.jsonPrimitive?.content
    InputData(contextStr, expressionStr, resource, variables, terminologyServer)
}

private fun createOperationOutcome(severity: String, code: String, message: String): JsonObject {
    return buildJsonObject {
        put("resourceType", "OperationOutcome")
        put("issue", buildJsonArray {
            add(buildJsonObject {
                put("severity", severity)
                put("code", code)
                put("details", buildJsonObject {
                    put("text", message)
                })
            })
        })
    }
}

