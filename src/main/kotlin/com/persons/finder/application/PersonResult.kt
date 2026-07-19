package com.persons.finder.application

import com.persons.finder.domain.model.PersonId
import com.persons.finder.domain.model.PersonProfile
import java.time.Instant

data class PersonResult(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: String,
    val createdAt: Instant,
    val lastKnownLocationAt: Instant,
)
