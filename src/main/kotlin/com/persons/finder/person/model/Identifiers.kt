package com.persons.finder.person.model

import java.util.UUID

@JvmInline
value class PersonId private constructor(val value: UUID) : Comparable<PersonId> {
    override fun compareTo(other: PersonId): Int = compareUuidBytes(value, other.value)

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PersonId = PersonId(UUID.randomUUID())

        fun from(value: UUID): PersonId {
            requireServerUuidV4(value)
            return PersonId(value)
        }
    }
}

@JvmInline
value class ObservationId private constructor(val value: UUID) : Comparable<ObservationId> {
    override fun compareTo(other: ObservationId): Int = compareUuidBytes(value, other.value)

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ObservationId = ObservationId(UUID.randomUUID())

        fun from(value: UUID): ObservationId {
            requireServerUuidV4(value)
            return ObservationId(value)
        }
    }
}

@JvmInline
value class ClientUpdateId private constructor(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun from(value: UUID): ClientUpdateId {
            require(value != NIL_UUID) { "clientUpdateId must not be nil" }
            require(value.variant() == IETF_UUID_VARIANT) {
                "clientUpdateId must use the standard UUID variant"
            }
            return ClientUpdateId(value)
        }
    }
}

private val NIL_UUID = UUID(0, 0)
private const val IETF_UUID_VARIANT = 2
private const val RANDOM_UUID_VERSION = 4

private fun requireServerUuidV4(value: UUID) {
    require(value.variant() == IETF_UUID_VARIANT && value.version() == RANDOM_UUID_VERSION) {
        "Server identifiers must be UUIDv4 values"
    }
}

private fun compareUuidBytes(left: UUID, right: UUID): Int {
    val mostSignificant =
        java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (mostSignificant != 0) {
        mostSignificant
    } else {
        java.lang.Long.compareUnsigned(left.leastSignificantBits, right.leastSignificantBits)
    }
}
