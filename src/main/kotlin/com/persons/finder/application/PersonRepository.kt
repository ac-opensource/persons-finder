package com.persons.finder.application

import com.persons.finder.domain.model.ClientUpdateId
import com.persons.finder.domain.model.LastKnownLocationProjection
import com.persons.finder.domain.model.LocationObservation
import com.persons.finder.domain.model.PersonId
import com.persons.finder.domain.model.PersonProfile
import java.time.Instant

interface PersonRepository {
    fun insertPerson(person: NewPerson)

    fun insertObservation(observation: LocationObservation)

    fun insertLastKnown(projection: LastKnownLocationProjection)

    fun lockLastKnown(personId: PersonId): LastKnownLocationProjection?

    fun findObservation(
        personId: PersonId,
        clientUpdateId: ClientUpdateId,
    ): LocationObservation?

    fun updateLastKnown(projection: LastKnownLocationProjection)
}

data class NewPerson(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: String,
    val createdAt: Instant,
)
