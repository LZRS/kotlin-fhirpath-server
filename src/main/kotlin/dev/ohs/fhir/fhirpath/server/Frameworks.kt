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
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.configureFrameworks() {
  dependencies {
    provide<FhirR4Service> { FhirR4Service() }
    provide<FhirR4bService> { FhirR4bService() }
    provide<FhirR5Service> { FhirR5Service() }
  }
}
