package com.persons.finder.infrastructure.bio

import com.persons.finder.application.BioGenerator
import com.persons.finder.application.BioPolicy
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class BioConfiguration {
    @Bean
    fun bioGenerator(
        @Value("\${persons.bio.generator}") configuredGenerator: String,
        @Value("\${persons.runtime.mode}") runtimeMode: String,
    ): BioGenerator =
        when {
            configuredGenerator == "deterministic" &&
                runtimeMode in DETERMINISTIC_RUNTIME_MODES -> DeterministicBioGenerator()

            else ->
                throw BeanCreationException(
                    "bioGenerator",
                    "Unsupported bio generator configuration",
                )
        }

    @Bean
    fun bioPolicy(): BioPolicy = BioPolicy()

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    private companion object {
        val DETERMINISTIC_RUNTIME_MODES = setOf("assessment-local", "test")
    }
}
