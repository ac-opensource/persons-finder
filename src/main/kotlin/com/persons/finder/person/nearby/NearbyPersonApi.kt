package com.persons.finder.person.nearby

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

data class NearbyPersonResponse(
    val id: UUID,
    val name: String,
    val jobTitle: String,
    @field:ArraySchema(schema = Schema(nullable = false))
    val hobbies: List<String>,
    @field:Schema(minLength = 1, maxLength = 320, pattern = ".*\\S.*")
    val bio: String,
    @field:Schema(type = "string", format = "date-time")
    val createdAt: String,
    @field:Schema(type = "string", format = "date-time")
    val lastKnownLocationAt: String,
    @field:Schema(
        description = "Spheroidal distance in kilometres, rounded to one decimal for display",
        minimum = "0.0",
        maximum = "100.0",
    )
    val distanceKm: BigDecimal,
)
