/*
 * Copyright 2026 Open Health Stack Foundation
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

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FhirPathRequestValidationTest {

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun missingParameterFieldReturnsBadRequest(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody("""{"resourceType":"Parameters"}""")
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("OperationOutcome"))
    assertTrue(body.contains("Missing required field: 'parameter'"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun missingResourceParameterReturnsBadRequest(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody(
          """
          {
            "resourceType": "Parameters",
            "parameter": [
              {"name": "expression", "valueString": "name.given"}
            ]
          }
          """
            .trimIndent()
        )
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("OperationOutcome"))
    assertTrue(body.contains("Missing required parameter: 'resource'"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun missingExpressionParameterReturnsBadRequest(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody(
          """
          {
            "resourceType": "Parameters",
            "parameter": [
              {"name": "resource", "resource": {"resourceType": "Patient"}}
            ]
          }
          """
            .trimIndent()
        )
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("OperationOutcome"))
    assertTrue(body.contains("Missing required parameter: 'expression'"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun invalidResourceTypeReturnsBadRequest(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody("""{"resourceType":"Patient"}""")
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("OperationOutcome"))
    assertTrue(body.contains("Expected FHIR Parameters resource"))
  }
}
