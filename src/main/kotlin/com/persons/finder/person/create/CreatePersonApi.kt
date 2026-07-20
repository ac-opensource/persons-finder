package com.persons.finder.person.create

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

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
                nullable = false,
            ),
    )
    val hobbies: List<String?>? = null,
    val location: CreatePersonLocationRequest? = null,
)

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class CreatePersonLocationRequest(
    @field:Schema(minimum = "-90", maximum = "90")
    val latitude: Double? = null,
    @field:Schema(minimum = "-180", maximum = "180")
    val longitude: Double? = null,
)

data class CreatePersonResponse(
    val id: UUID,
    val name: String,
    val jobTitle: String,
    val hobbies: List<String>,
    @field:Schema(minLength = 1, maxLength = 320, pattern = ".*\\S.*")
    val bio: String,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val createdAt: String,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val lastKnownLocationAt: String,
)

private const val UTC_MILLISECOND_PATTERN =
    "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:[0-5]\\d\\.\\d{3}Z$"
