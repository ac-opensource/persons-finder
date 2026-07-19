package com.persons.finder.application

import com.persons.finder.domain.model.GeoPoint
import com.persons.finder.domain.model.PersonProfile

fun interface CreatePersonUseCase {
    fun execute(command: CreatePersonCommand): CreatePersonOutcome
}

data class CreatePersonCommand(
    val profile: PersonProfile,
    val initialLocation: GeoPoint,
)

sealed interface CreatePersonOutcome {
    data class Created(val person: PersonResult) : CreatePersonOutcome

    data object BioCompositionDoesNotFit : CreatePersonOutcome

    data object BioInputRejected : CreatePersonOutcome

    data object BioGenerationUnavailable : CreatePersonOutcome
}
