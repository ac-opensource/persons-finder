package com.persons.finder.presentation.api

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class CreatePersonRequest(
    @field:Schema(description = "1-80 Unicode code points after outer trim and NFC normalization")
    val name: String? = null,
    @field:Schema(description = "1-80 Unicode code points after outer trim and NFC normalization")
    val jobTitle: String? = null,
    @field:ArraySchema(
        minItems = 1,
        maxItems = 10,
        schema =
            Schema(
                description = "1-60 Unicode code points after outer trim and NFC normalization",
            ),
    )
    val hobbies: List<String>? = null,
    val location: LocationRequest? = null,
)

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class LocationRequest(
    @field:Schema(minimum = "-90", maximum = "90")
    val latitude: Double? = null,
    @field:Schema(minimum = "-180", maximum = "180")
    val longitude: Double? = null,
)

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class UpdatePersonLocationRequest(
    @field:Schema(minimum = "-90", maximum = "90")
    val latitude: Double? = null,
    @field:Schema(minimum = "-180", maximum = "180")
    val longitude: Double? = null,
    @field:Schema(type = "string", format = "date-time", pattern = CAPTURED_AT_PATTERN)
    val capturedAt: String? = null,
    @field:Schema(type = "string", format = "uuid")
    val clientUpdateId: String? = null,
)

private const val CAPTURED_AT_PATTERN =
    "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:[0-5]\\d(?:\\.\\d{1,3})?(?:Z|[+-]\\d{2}:\\d{2})$"
