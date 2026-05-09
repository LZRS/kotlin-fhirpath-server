package dev.ohs.dev.ohs.fhir.fhirpath.server.services

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.dev.ohs.fhir.fhirpath.server.DynamicLookupSerializer
import dev.ohs.dev.ohs.fhir.fhirpath.server.InputData
import dev.ohs.dev.ohs.fhir.fhirpath.server.toLocalTime
import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Address
import dev.ohs.fhir.model.r4.Age
import dev.ohs.fhir.model.r4.Annotation as FhirAnnotation
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Base64Binary
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactDetail
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Contributor
import dev.ohs.fhir.model.r4.Count
import dev.ohs.fhir.model.r4.DataRequirement
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Distance
import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.model.r4.Duration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Id
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Instant
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Money
import dev.ohs.fhir.model.r4.Oid
import dev.ohs.fhir.model.r4.ParameterDefinition
import dev.ohs.fhir.model.r4.Integer as FhirInteger
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Parameters
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Range
import dev.ohs.fhir.model.r4.Ratio
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RelatedArtifact
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.SampledData
import dev.ohs.fhir.model.r4.Signature
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Timing
import dev.ohs.fhir.model.r4.TriggerDefinition
import dev.ohs.fhir.model.r4.UnsignedInt
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.Url
import dev.ohs.fhir.model.r4.UsageContext
import dev.ohs.fhir.model.r4.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import dev.ohs.fhir.model.r4.String as FhirString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class FhirR4Service: FhirpathService {
    private val resourceParser = FhirR4Json()
    private val json = Json { }
    private val fhirpathEngine = FhirPathEngine.forR4()

    override suspend fun evaluate(inputData: InputData): JsonElement = withContext(Dispatchers.Default) {
        val resourceString = json.encodeToString(inputData.resource)
        val resource = resourceParser.decodeFromString(resourceString)
        val resourceType = resource::class.simpleName!!
        val results = if (!inputData.contextExpression.isNullOrBlank()) {
                val contextResult = fhirpathEngine.evaluateExpression(inputData.contextExpression, base = resource, variables = inputData.variables)
                contextResult.mapIndexed { index, contextValue ->
                    val label = "$resourceType.${inputData.contextExpression}[$index]"
                    val expressionResult = fhirpathEngine.evaluateExpression(inputData.expression, base = contextValue, variables = inputData.variables)
                    val expressResultAsParameters = expressionResult.map { convertEvalResultToParameter(it) }
                    val traceDataAsParameters = fhirpathEngine.traces.map { entry ->
                        Parameters.Parameter(
                            name = FhirString(value = "trace"),
                            value = Parameters.Parameter.Value.String(value = FhirString(value = entry.key)),
                            part = entry.value.map { convertEvalResultToParameter(it.value, it.path) }
                        )
                    }

                    Parameters.Parameter(
                        name = FhirString(value = "result"),
                        value = Parameters.Parameter.Value.String(value = FhirString(value = label)),
                        part = expressResultAsParameters + traceDataAsParameters
                    )
                }
            } else {
                val expressionResult = fhirpathEngine.evaluateExpression(inputData.expression, base = resource, variables = inputData.variables)
                val expressResultAsParameters = expressionResult.map { convertEvalResultToParameter(it) }
                val traceDataAsParameters = fhirpathEngine.traces.map { entry ->
                    Parameters.Parameter(
                        name = FhirString(value ="trace"),
                        value = Parameters.Parameter.Value.String(value = FhirString(value = entry.key)),
                        part = entry.value.map { convertEvalResultToParameter(it.value, it.path) }
                    )
                }

                listOf(Parameters.Parameter(
                    name = FhirString(value = "result"),
                    part = expressResultAsParameters + traceDataAsParameters
                ))
            }

        val evaluationParameters = Parameters(
            id = "fhirpath",
            parameter = listOf(
                Parameters.Parameter(name = FhirString(value = "parameters"),
                    part = buildList {
                        add(Parameters.Parameter(name = FhirString(value = "evaluator"),
                            value = Parameters.Parameter.Value.String(value = FhirString(value = "Kotlin FHIRPath (R4)"))))

                        inputData.contextExpression?.let {
                            add(Parameters.Parameter(name = FhirString(value = "context"),
                                value = Parameters.Parameter.Value.String(value = FhirString(value = inputData.contextExpression))))
                        }

                        add(Parameters.Parameter(name = FhirString(value = "expression"),
                            value = Parameters.Parameter.Value.String(value = FhirString(value = inputData.expression))))

                        add(Parameters.Parameter(name = FhirString(value = "resource"), resource = resource,))

                        if (inputData.variables.isNotEmpty()){
                            add(Parameters.Parameter(name = FhirString(value = "variables"),
                                part = inputData.variables.map {
                                    Parameters.Parameter(name = FhirString(value = it.key), value = Parameters.Parameter.Value.String(value = FhirString(value = it.value)))
                                }))
                        }
                    })) + results
        )
        val parametersString = resourceParser.encodeToString(evaluationParameters)
        json.parseToJsonElement(parametersString)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun convertEvalResultToParameter(evalResult: Any, resourcePath: String? = null): Parameters.Parameter {
        return when(evalResult) {
            is BigDecimal -> Parameters.Parameter(name = FhirString(value = "decimal"), value = Parameters.Parameter.Value.Decimal(value = Decimal(
                value = evalResult
            )
            ))
            is FhirPathQuantity -> Parameters.Parameter(name = FhirString(value = "quantity"), value = Parameters.Parameter.Value.Quantity(value = Quantity(
                value = Decimal(value = evalResult.value),
                unit = FhirString(value = evalResult.unit),
            )
            ),)
            is FhirPathDateTime -> Parameters.Parameter(name = FhirString(value = "dateTime"), value = Parameters.Parameter.Value.DateTime(value = DateTime(
                value = FhirDateTime.fromString(evalResult.toString())
            )
            ))
            is FhirPathDate -> Parameters.Parameter(name = FhirString(value = "date"), value = Parameters.Parameter.Value.Date(value = Date(
                value = FhirDate.fromString(evalResult.toString())
            )
            ))
            is FhirPathTime -> Parameters.Parameter(name = FhirString(value = "time"), value = Parameters.Parameter.Value.Time(value = Time(value = evalResult.toLocalTime())))
            is Int -> Parameters.Parameter(name = FhirString(value = "integer"),value = Parameters.Parameter.Value.Integer(value = FhirInteger(
                value = evalResult
            )
            ),)
            is Boolean -> Parameters.Parameter(name = FhirString(value = "boolean"), value = Parameters.Parameter.Value.Boolean(value = FhirBoolean(value = evalResult)))
            is String -> Parameters.Parameter(name = FhirString(value = "string"), value = Parameters.Parameter.Value.String(value = FhirString(value = evalResult)))

            is Base64Binary -> Parameters.Parameter(name = FhirString(value = "base64Binary"), value = Parameters.Parameter.Value.Base64Binary(evalResult))
            is Canonical -> Parameters.Parameter(name = FhirString(value = "canonical"), value = Parameters.Parameter.Value.Canonical(evalResult))
            is Code -> Parameters.Parameter(name = FhirString(value = "code"), value = Parameters.Parameter.Value.Code(evalResult))
            is Date -> Parameters.Parameter(name = FhirString(value = "date"), value = Parameters.Parameter.Value.Date(evalResult))
            is Id -> Parameters.Parameter(name = FhirString(value = "id"), value = Parameters.Parameter.Value.Id(evalResult))
            is Instant -> Parameters.Parameter(name = FhirString(value = "instant"), value = Parameters.Parameter.Value.Instant(evalResult))
            is Markdown -> Parameters.Parameter(name = FhirString(value = "markdown"), value = Parameters.Parameter.Value.Markdown(evalResult))
            is Oid -> Parameters.Parameter(name = FhirString(value = "oid"), value = Parameters.Parameter.Value.Oid(evalResult))
            is PositiveInt -> Parameters.Parameter(name = FhirString(value = "positiveInt"), value = Parameters.Parameter.Value.PositiveInt(evalResult))
            is UnsignedInt -> Parameters.Parameter(name = FhirString(value = "unsignedInt"), value = Parameters.Parameter.Value.UnsignedInt(evalResult))
            is Uuid -> Parameters.Parameter(name = FhirString(value = "uuid"), value = Parameters.Parameter.Value.Uuid(evalResult))
            is Url -> Parameters.Parameter(name = FhirString(value = "url"), value = Parameters.Parameter.Value.Url(evalResult))
            is Uri -> Parameters.Parameter(name = FhirString(value = "uri"), value = Parameters.Parameter.Value.Uri(evalResult))
            is Address -> Parameters.Parameter(name = FhirString(value = "address"), value = Parameters.Parameter.Value.Address(evalResult))
            is Age -> Parameters.Parameter(name = FhirString(value = "age"), value = Parameters.Parameter.Value.Age(evalResult))
            is FhirAnnotation -> Parameters.Parameter(name = FhirString(value = "annotation"), value = Parameters.Parameter.Value.Annotation(evalResult))
            is Attachment -> Parameters.Parameter(name = FhirString(value = "attachment"), value = Parameters.Parameter.Value.Attachment(evalResult))
            is CodeableConcept -> Parameters.Parameter(name = FhirString(value = "codeableConcept"), value = Parameters.Parameter.Value.CodeableConcept(evalResult))
            is Coding -> Parameters.Parameter(name = FhirString(value = "coding"), value = Parameters.Parameter.Value.Coding(evalResult))
            is ContactPoint -> Parameters.Parameter(name = FhirString(value = "contactPoint"), value = Parameters.Parameter.Value.ContactPoint(evalResult))
            is Count -> Parameters.Parameter(name = FhirString(value = "count"), value = Parameters.Parameter.Value.Count(evalResult))
            is Distance -> Parameters.Parameter(name = FhirString(value = "distance"), value = Parameters.Parameter.Value.Distance(evalResult))
            is Duration -> Parameters.Parameter(name = FhirString(value = "duration"), value = Parameters.Parameter.Value.Duration(evalResult))
            is HumanName -> Parameters.Parameter(name = FhirString(value = "humanName"), value = Parameters.Parameter.Value.HumanName(evalResult))
            is Identifier -> Parameters.Parameter(name = FhirString(value = "identifier"), value = Parameters.Parameter.Value.Identifier(evalResult))
            is Money -> Parameters.Parameter(name = FhirString(value = "money"), value = Parameters.Parameter.Value.Money(evalResult))
            is Period -> Parameters.Parameter(name = FhirString(value = "period"), value = Parameters.Parameter.Value.Period(evalResult))
            is Quantity -> Parameters.Parameter(name = FhirString(value = "quantity"), value = Parameters.Parameter.Value.Quantity(evalResult))
            is Range -> Parameters.Parameter(name = FhirString(value = "range"), value = Parameters.Parameter.Value.Range(evalResult))
            is Ratio -> Parameters.Parameter(name = FhirString(value = "ratio"), value = Parameters.Parameter.Value.Ratio(evalResult))
            is Reference -> Parameters.Parameter(name = FhirString(value = "reference"), value = Parameters.Parameter.Value.Reference(evalResult))
            is SampledData -> Parameters.Parameter(name = FhirString(value = "sampledData"), value = Parameters.Parameter.Value.SampledData(evalResult))
            is Signature -> Parameters.Parameter(name = FhirString(value = "signature"), value = Parameters.Parameter.Value.Signature(evalResult))
            is Timing -> Parameters.Parameter(name = FhirString(value = "timing"), value = Parameters.Parameter.Value.Timing(evalResult))
            is ContactDetail -> Parameters.Parameter(name = FhirString(value = "contactDetail"), value = Parameters.Parameter.Value.ContactDetail(evalResult))
            is Contributor -> Parameters.Parameter(name = FhirString(value = "contributor"), value = Parameters.Parameter.Value.Contributor(evalResult))
            is DataRequirement -> Parameters.Parameter(name = FhirString(value = "dataRequirement"), value = Parameters.Parameter.Value.DataRequirement(evalResult))
            is Expression -> Parameters.Parameter(name = FhirString(value = "expression"), value = Parameters.Parameter.Value.Expression(evalResult))
            is ParameterDefinition -> Parameters.Parameter(name = FhirString(value = "parameterDefinition"), value = Parameters.Parameter.Value.ParameterDefinition(evalResult))
            is RelatedArtifact -> Parameters.Parameter(name = FhirString(value = "relatedArtifact"), value = Parameters.Parameter.Value.RelatedArtifact(evalResult))
            is TriggerDefinition -> Parameters.Parameter(name = FhirString(value = "triggerDefinition"), value = Parameters.Parameter.Value.TriggerDefinition(evalResult))
            is UsageContext -> Parameters.Parameter(name = FhirString(value = "usageContext"), value = Parameters.Parameter.Value.UsageContext(evalResult))
            is Dosage -> Parameters.Parameter(name = FhirString(value = "dosage"), value = Parameters.Parameter.Value.Dosage(evalResult))
            is Meta -> Parameters.Parameter(name = FhirString(value = "meta"), value = Parameters.Parameter.Value.Meta(evalResult))
            else -> Parameters.Parameter(
                extension = listOf(
                    Extension(url = "http://fhir.forms-lab.com/StructureDefinition/json-value",
                        value = Extension.Value.String(value = FhirString(value = json.encodeToString(
                            DynamicLookupSerializer(), evalResult))))
                ),
                name = FhirString(value = evalResult::class.simpleName?.lowercase()),
            )
        }
//            .let {
//            if (!resourcePath.isNullOrBlank()) {
//                val extension = it.extension
//                it.copy(extension = extension + Extension(url = "http://fhir.forms-lab.com/StructureDefinition/resource-path", value = Extension.Value.String(value = FhirString(value = resourcePath))))
//            } else it
//        }
    }
}