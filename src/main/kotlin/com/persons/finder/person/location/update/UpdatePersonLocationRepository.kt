package com.persons.finder.person.location.update

import com.persons.finder.person.model.ClientUpdateId
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.PersonId

interface UpdatePersonLocationRepository {
    fun lockLastKnown(personId: PersonId): LastKnownLocationProjection?

    fun findObservation(
        personId: PersonId,
        clientUpdateId: ClientUpdateId,
    ): LocationObservation?

    fun insertObservation(observation: LocationObservation)

    fun updateLastKnown(projection: LastKnownLocationProjection)
}
