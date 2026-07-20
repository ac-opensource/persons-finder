package com.persons.finder.person.bio.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LiveAiTestAuthorizationTest {
    @Test
    fun `selected provider requires one exact supported wire value`() {
        assertEquals(
            "OPENAI",
            LiveAiTestAuthorization.selectedProvider(mapOf("LIVE_AI_PROVIDER" to "openai")::get),
        )
        assertEquals(
            "GEMINI",
            LiveAiTestAuthorization.selectedProvider(mapOf("LIVE_AI_PROVIDER" to "gemini")::get),
        )
        assertEquals(
            "ANTHROPIC",
            LiveAiTestAuthorization.selectedProvider(
                mapOf("LIVE_AI_PROVIDER" to "anthropic")::get,
            ),
        )

        listOf(null, "", "OPENAI", " openai", "openai ", "other").forEach { invalidValue ->
            val environment =
                if (invalidValue == null) {
                    emptyMap()
                } else {
                    mapOf("LIVE_AI_PROVIDER" to invalidValue)
                }
            assertThrows<IllegalArgumentException> {
                LiveAiTestAuthorization.selectedProvider(environment::get)
            }
        }
    }

    @Test
    fun `authorization requires exact lowercase true for every scoped control`() {
        val approved =
            LiveAiTestAuthorization.requirements("GEMINI")
                .associate { requirement -> requirement.environmentName to "true" }

        assertEquals(
            emptyList<LiveAiTestAuthorizationRequirement>(),
            LiveAiTestAuthorization.unmetRequirements("GEMINI", approved::get),
        )

        listOf(null, "false", "TRUE", " true", "true ").forEach { invalidValue ->
            val environment =
                approved.toMutableMap().apply {
                    val name =
                        "GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED"
                    if (invalidValue == null) remove(name) else put(name, invalidValue)
                }
            assertEquals(
                listOf("GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED"),
                LiveAiTestAuthorization.unmetRequirements("GEMINI", environment::get)
                    .map(LiveAiTestAuthorizationRequirement::environmentName),
            )
        }
    }

    @Test
    fun `synthetic data-use approval is provider scoped and old logging flag is ignored`() {
        val environment =
            LiveAiTestAuthorization.requirements("GEMINI")
                .associate { requirement -> requirement.environmentName to "true" }
                .toMutableMap()
                .apply {
                    remove("GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED")
                    put("GEMINI_LIVE_CONTENT_LOGGING_DISABLED_CONFIRMED", "true")
                }

        assertEquals(
            listOf("GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED"),
            LiveAiTestAuthorization.unmetRequirements("GEMINI", environment::get)
                .map(LiveAiTestAuthorizationRequirement::environmentName),
        )
        assertTrue(
            LiveAiTestAuthorization.unmetRequirements("OPENAI", environment::get).isNotEmpty(),
        )
    }

    @Test
    fun `provider metadata is constrained before deriving an environment name`() {
        assertThrows<IllegalArgumentException> {
            LiveAiTestAuthorization.requirements("gemini")
        }
        assertThrows<IllegalArgumentException> {
            LiveAiTestAuthorization.requirements("GEMINI-NAME")
        }
    }
}
