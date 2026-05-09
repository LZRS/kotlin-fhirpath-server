package dev.ohs.dev.ohs.fhir.fhirpath.server

import dev.ohs.fhir.fhirpath.types.FhirPathTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

data class InputData(val contextExpression: String?,
                     val expression: String,
                     val resource: JsonObject,
                     val variables: Map<String, String>,
                     val terminologyServer: String?)

class MissingRequiredFieldException(message: String): SerializationException(message = message)

fun FhirPathTime.toLocalTime(): LocalTime = LocalTime(
    hour   = this.hour,
    minute = this.minute ?: 0,
    second = this.second?.toInt() ?: 0,
    nanosecond = this.second?.let { s ->
        ((s - s.toInt()) * 1_000_000_000).toInt()
    } ?: 0,
)

@ExperimentalSerializationApi
class DynamicLookupSerializer: KSerializer<Any> {
    override val descriptor: SerialDescriptor = ContextualSerializer(Any::class, null, emptyArray()).descriptor

    @OptIn(InternalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Any) {
        val actualSerializer = encoder.serializersModule.getContextual(value::class) ?: value::class.serializer()
        @Suppress("UNCHECKED_CAST")
        encoder.encodeSerializableValue(actualSerializer as KSerializer<Any>, value)
    }

    override fun deserialize(decoder: Decoder): Any {
        error("Unsupported")
    }
}
