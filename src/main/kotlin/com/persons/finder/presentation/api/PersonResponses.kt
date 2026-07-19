package com.persons.finder.presentation.api

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class PersonResponse(
    val id: UUID,
    val name: String,
    val jobTitle: String,
    val hobbies: List<String>,
    @field:Schema(minLength = 1, maxLength = 240, pattern = ".*\\S.*")
    val bio: String,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val createdAt: String,
    @field:Schema(type = "string", format = "date-time", pattern = UTC_MILLISECOND_PATTERN)
    val lastKnownLocationAt: String,
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

private const val UTC_MILLISECOND_PATTERN =
    "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:[0-5]\\d\\.\\d{3}Z$"
