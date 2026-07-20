package com.persons.finder.person.create

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException

class BioConfigurationTest {
    @Test
    fun `unknown bio adapter configuration fails instead of falling back`() {
        assertThrows(BeanCreationException::class.java) {
            BioConfiguration().bioGenerator("network-or-unknown", "test")
        }
    }

    @Test
    fun `deterministic adapter cannot be selected as a production default`() {
        assertThrows(BeanCreationException::class.java) {
            BioConfiguration().bioGenerator("deterministic", "production")
        }
    }
}
