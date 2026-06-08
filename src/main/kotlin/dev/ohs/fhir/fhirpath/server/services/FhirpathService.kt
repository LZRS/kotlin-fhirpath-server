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
package dev.ohs.fhir.fhirpath.server.services

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.TraceEntry
import dev.ohs.fhir.fhirpath.server.InputData
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Base implementation of [FhirPathService] for a specific FHIR version.
 *
 * To add support for a new FHIR version, extend this class with:
 * - [Param] — the version's `Parameters.Parameter` type
 * - [Resource] — the version's base `Resource` type
 *
 * ## Minimum required overrides
 * |Member                 |Purpose                                                                                          |
 * |-----------------------|-------------------------------------------------------------------------------------------------|
 * |[evaluatorLabel]       |Human-readable label included in the `evaluator` output parameter (e.g. `"Kotlin FHIRPath (R4)"`)|
 * |[getFhirPathEngine]    |Get Version-specific [FhirPathEngine] instance (e.g. `FhirPathEngine.forR4()`)                       |
 * |[decodeResource]       |Deserialise a JSON string into the version's [Resource] type                                     |
 * |[buildFhirParameters]  |Serialise a `Parameters` resource (with the given [Param] list) back to a JSON string            |
 * |[makeStringParameter]  |Construct a string-valued `Parameters.Parameter` with an optional list of child parts            |
 * |[makeGroupParameter]   |Construct a group `Parameters.Parameter` (no value, only child parts)                            |
 * |[makeResourceParameter]|Construct a resource-valued `Parameters.Parameter`                                               |
 *
 * ## FHIRPath primitive type converters
 *
 * The engine returns its own primitive types for numeric, temporal, and scalar results. Each maps
 * to a specific FHIR parameter type; implement one method per primitive:
 *
 * | Method                     | Engine type        | FHIR parameter type |
 * |----------------------------|--------------------|---------------------|
 * | [makeDecimalParameter]     | [BigDecimal]       | `decimal`           |
 * | [makeQuantityParameter]    | [FhirPathQuantity] | `quantity`          |
 * | [makeDateTimeParameter]    | [FhirPathDateTime] | `dateTime`          |
 * | [makeDateParameter]        | [FhirPathDate]     | `date`              |
 * | [makeTimeParameter]        | [FhirPathTime]     | `time`              |
 * | [makeIntegerParameter]     | [Int]              | `integer`           |
 * | [makeBooleanParameter]     | [Boolean]          | `boolean`           |
 * | [makeStringValueParameter] | [String]           | `string`            |
 *
 * ## FHIR model type converter
 *
 * [convertFhirTypeToParameter] receives any value that is not one of the FHIRPath primitives above
 * — typically a version-specific FHIR data type (e.g. `Coding`, `Period`, `Reference`). Use a
 * `when` expression to wrap each recognised type in its corresponding `Parameters.Parameter.Value`
 * subclass. The `else` branch should fall back to a JSON extension parameter using
 * `DynamicLookupSerializer` for any unrecognised type.
 */
internal abstract class FhirPathService<Param : Any, Resource : Any>  {

  /** Human-readable label emitted as the `evaluator` output parameter. */
  protected abstract val evaluatorLabel: String

  /** JSON codec used to round-trip the final `Parameters` resource. */
  protected val json = Json {}

  /** Get Version-specific FHIRPath evaluation engine. */
  protected abstract fun getFhirPathEngine(): FhirPathEngine
  /** Deserialize [jsonString] into this version's [Resource] type. */
  protected abstract fun decodeResource(jsonString: String): Resource

  /** Wrap a [BigDecimal] engine result as a `decimal` parameter. */
  protected abstract fun makeDecimalParameter(value: BigDecimal): Param

  /** Wrap a [FhirPathQuantity] engine result as a `quantity` parameter. */
  protected abstract fun makeQuantityParameter(value: FhirPathQuantity): Param

  /** Wrap a [FhirPathDateTime] engine result as a `dateTime` parameter. */
  protected abstract fun makeDateTimeParameter(value: FhirPathDateTime): Param

  /** Wrap a [FhirPathDate] engine result as a `date` parameter. */
  protected abstract fun makeDateParameter(value: FhirPathDate): Param

  /** Wrap a [FhirPathTime] engine result as a `time` parameter. */
  protected abstract fun makeTimeParameter(value: FhirPathTime): Param

  /** Wrap an [Int] engine result as an `integer` parameter. */
  protected abstract fun makeIntegerParameter(value: Int): Param

  /** Wrap a [Boolean] engine result as a `boolean` parameter. */
  protected abstract fun makeBooleanParameter(value: Boolean): Param

  /** Wrap a [String] engine result as a `string` parameter. */
  protected abstract fun makeStringValueParameter(value: String): Param

  /**
   * Wrap a version-specific FHIR model type as a parameter.
   *
   * Called for any eval result that is not a FHIRPath primitive. Use a `when` expression over the
   * version's concrete data types (e.g. `Coding`, `Period`). The `else` branch should produce a
   * JSON extension fallback for unknown types.
   */
  protected abstract fun convertFhirTypeToParameter(value: Any): Param

  /**
   * Convert a single FHIRPath evaluation result to a [Param].
   *
   * Dispatches FHIRPath primitive types to the dedicated `make*Parameter` methods and delegates
   * everything else to [convertFhirTypeToParameter].
   */
  protected fun convertEvalResultToParameter(evalResult: Any): Param =
    when (evalResult) {
      is BigDecimal -> makeDecimalParameter(evalResult)
      is FhirPathQuantity -> makeQuantityParameter(evalResult)
      is FhirPathDateTime -> makeDateTimeParameter(evalResult)
      is FhirPathDate -> makeDateParameter(evalResult)
      is FhirPathTime -> makeTimeParameter(evalResult)
      is Int -> makeIntegerParameter(evalResult)
      is Boolean -> makeBooleanParameter(evalResult)
      is String -> makeStringValueParameter(evalResult)
      else -> convertFhirTypeToParameter(evalResult)
    }

  /**
   * Build a string-valued parameter named [name] with value [value], optionally with child [parts].
   */
  protected abstract fun makeStringParameter(
    name: String,
    value: String?,
    parts: List<Param> = emptyList(),
  ): Param

  /** Build a group parameter named [name] with no value and the given child [parts]. */
  protected abstract fun makeGroupParameter(name: String, parts: List<Param>): Param

  /** Build a resource-valued parameter named [name] containing [resource]. */
  protected abstract fun makeResourceParameter(name: String, resource: Resource): Param

  /**
   * Serialize a `Parameters` resource with the given [id] and top-level [params] to a JSON string.
   */
  protected abstract fun buildFhirParameters(id: String, params: List<Param>): String

  private fun buildTracingParameters(traces: Map<String, List<TraceEntry>>) = traces.map { entry ->
    makeStringParameter(
      name = "trace",
      value = entry.key,
      parts = entry.value.map { convertEvalResultToParameter(it.value) },
    )
  }

   suspend fun evaluate(inputData: InputData): JsonElement =
    withContext(Dispatchers.Default) {
      val fhirpathEngine = getFhirPathEngine()
      val resource = decodeResource(inputData.resourceStr)
      val resourceType = resource::class.simpleName!!

      val results =
        if (!inputData.contextExpression.isNullOrBlank()) {
          fhirpathEngine
            .evaluateExpression(
              inputData.contextExpression,
              base = resource,
              variables = inputData.variables,
            )
            .mapIndexed { index, contextValue ->
              val label = "$resourceType.${inputData.contextExpression}[$index]"
              val expressionResult =
                fhirpathEngine.evaluateExpression(
                  inputData.expression,
                  base = contextValue,
                  variables = inputData.variables,
                )
              makeStringParameter(
                name = "result",
                value = label,
                parts =
                  expressionResult.map { convertEvalResultToParameter(it) } + buildTracingParameters(fhirpathEngine.traces),
              )
            }
        } else {
          val expressionResult =
            fhirpathEngine.evaluateExpression(
              inputData.expression,
              base = resource,
              variables = inputData.variables,
            )
          listOf(
            makeGroupParameter(
              name = "result",
              parts = expressionResult.map { convertEvalResultToParameter(it) } + buildTracingParameters(fhirpathEngine.traces),
            )
          )
        }

      val metaParameters: List<Param> = buildList {
        add(makeStringParameter(name = "evaluator", value = evaluatorLabel))
        inputData.contextExpression?.let { add(makeStringParameter(name = "context", value = it)) }
        add(makeStringParameter(name = "expression", value = inputData.expression))
        add(makeResourceParameter(name = "resource", resource = resource))
        if (inputData.variables.isNotEmpty()) {
          add(
            makeGroupParameter(
              name = "variables",
              parts =
                inputData.variables.map { makeStringParameter(name = it.key, value = it.value) },
            )
          )
        }
      }

      val parametersJsonString =
        buildFhirParameters(
          id = "fhirpath",
          params = listOf(makeGroupParameter(name = "parameters", parts = metaParameters)) + results,
        )

      json.parseToJsonElement(parametersJsonString)
    }
}
