package com.persons.finder.person.nearby

fun interface NearbyPersonRepository {
    fun find(query: FindNearbyQuery): List<NearbyPerson>
}
