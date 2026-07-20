package com.persons.finder.person.location.update

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

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

data class UpdatePersonLocationResponse(
    val personId: UUID,
    val observationId: UUID,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val capturedAt: String,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val receivedAt: String,
    val lastKnownObservationId: UUID,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val lastKnownLocationAt: String,
)

private const val CAPTURED_AT_PATTERN =
    "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:[0-5]\\d(?:\\.\\d{1,3})?(?:Z|[+-]\\d{2}:\\d{2})$"

private const val UTC_MILLISECOND_PATTERN =
    "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:[0-5]\\d\\.\\d{3}Z$"
