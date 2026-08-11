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

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FhirPathRoutingTest {

  val requestBodyJson =
    """
                {
                    "resourceType": "Parameters",
                    "parameter": [
                        {
                            "name": "expression",
                            "valueString": "trace('trc').given.join(' ')\n.combine(family).join(', ')"
                        },
                        {
                            "name": "context",
                            "valueString": "name"
                        },
                        {
                            "name": "validate",
                            "valueBoolean": true
                        },
                        {
                            "name": "variables"
                        },
                        {
                            "name": "resource",
                            "resource": {
                                "resourceType": "Patient",
                                "id": "example",
                                "identifier": [
                                    {
                                        "use": "usual",
                                        "type": {
                                            "coding": [
                                                {
                                                    "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                                                    "code": "MR"
                                                }
                                            ]
                                        },
                                        "system": "urn:oid:1.2.36.146.595.217.0.1",
                                        "value": "12345",
                                        "period": {
                                            "start": "2001-05-06"
                                        },
                                        "assigner": {
                                            "display": "Acme Healthcare"
                                        }
                                    }
                                ],
                                "active": true,
                                "name": [
                                    {
                                        "use": "official",
                                        "family": "Chalmers",
                                        "given": [
                                            "Peter",
                                            "James"
                                        ]
                                    },
                                    {
                                        "use": "usual",
                                        "given": [
                                            "Jim"
                                        ]
                                    },
                                    {
                                        "use": "maiden",
                                        "family": "Windsor",
                                        "given": [
                                            "Peter",
                                            "James"
                                        ],
                                        "period": {
                                            "end": "2002"
                                        }
                                    }
                                ],
                                "telecom": [
                                    {
                                        "use": "home"
                                    },
                                    {
                                        "system": "phone",
                                        "value": "(03) 5555 6473",
                                        "use": "work",
                                        "rank": 1
                                    },
                                    {
                                        "system": "phone",
                                        "value": "(03) 3410 5613",
                                        "use": "mobile",
                                        "rank": 2
                                    },
                                    {
                                        "system": "phone",
                                        "value": "(03) 5555 8834",
                                        "use": "old",
                                        "period": {
                                            "end": "2014"
                                        }
                                    }
                                ],
                                "gender": "male",
                                "birthDate": "1974-12-25",
                                "_birthDate": {
                                    "extension": [
                                        {
                                            "url": "http://hl7.org/fhir/StructureDefinition/patient-birthTime",
                                            "valueDateTime": "1974-12-25T14:35:45-05:00"
                                        }
                                    ]
                                },
                                "deceasedBoolean": false,
                                "address": [
                                    {
                                        "use": "home",
                                        "type": "both",
                                        "text": "534 Erewhon St PeasantVille, Rainbow, Vic  3999",
                                        "line": [
                                            "534 Erewhon St"
                                        ],
                                        "city": "PleasantVille",
                                        "district": "Rainbow",
                                        "state": "Vic",
                                        "postalCode": "3999",
                                        "period": {
                                            "start": "1974-12-25"
                                        }
                                    }
                                ],
                                "contact": [
                                    {
                                        "relationship": [
                                            {
                                                "coding": [
                                                    {
                                                        "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                        "code": "N"
                                                    }
                                                ]
                                            }
                                        ],
                                        "name": {
                                            "family": "du Marché",
                                            "_family": {
                                                "extension": [
                                                    {
                                                        "url": "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix",
                                                        "valueString": "VV"
                                                    }
                                                ]
                                            },
                                            "given": [
                                                "Bénédicte"
                                            ]
                                        },
                                        "telecom": [
                                            {
                                                "system": "phone",
                                                "value": "+33 (237) 998327"
                                            }
                                        ],
                                        "address": {
                                            "use": "home",
                                            "type": "both",
                                            "line": [
                                                "534 Erewhon St"
                                            ],
                                            "city": "PleasantVille",
                                            "district": "Rainbow",
                                            "state": "Vic",
                                            "postalCode": "3999",
                                            "period": {
                                                "start": "1974-12-25"
                                            }
                                        },
                                        "gender": "female",
                                        "period": {
                                            "start": "2012"
                                        }
                                    }
                                ],
                                "managingOrganization": {
                                    "reference": "Organization/1"
                                }
                            }
                        },
                        {
                            "name": "terminologyserver",
                            "valueString": "https://sqlonfhir-r4.azurewebsites.net/fhir"
                        }
                    ]
                }
            """
      .trimIndent()

  @Test
  fun rootEndpointReturnsServerInfo() = testApplication {
    application { module() }
    val response = client.get("/")
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains("Kotlin FHIRPath server is running!"))
    assertTrue(body.contains(""""version":""""))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun endpointReturnsFhirParameters(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody(requestBodyJson)
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains(""""resourceType":"Parameters""""))
    assertTrue(body.contains(""""id":"fhirpath""""))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/fhirpath-r4", "/fhirpath-r4b", "/fhirpath-r5"])
  fun timeExpressionWithFractionalSecondsIsEvaluated(endpoint: String) = testApplication {
    application { module() }

    val response =
      client.post(endpoint) {
        contentType(ContentType(ContentType.Application.TYPE, "fhir+json"))
        setBody(
          """
          {
              "resourceType": "Parameters",
              "parameter": [
                  {
                      "name": "expression",
                      "valueString": "@T14:30:00.123"
                  },
                  {
                      "name": "resource",
                      "resource": {
                          "resourceType": "Patient"
                      }
                  }
              ]
          }
          """
            .trimIndent()
        )
      }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.bodyAsText()
    assertTrue(body.contains(""""valueTime":"14:30:00.123""""))
  }
}
