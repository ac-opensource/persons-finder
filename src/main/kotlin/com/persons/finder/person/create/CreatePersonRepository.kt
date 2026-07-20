package com.persons.finder.person.create

import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import java.time.Instant

interface CreatePersonRepository {
    fun insertPerson(person: NewPerson)

    fun insertObservation(observation: LocationObservation)

    fun insertLastKnown(projection: LastKnownLocationProjection)
}

data class NewPerson(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: String,
    val createdAt: Instant,
)
