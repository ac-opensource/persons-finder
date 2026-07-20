package com.persons.finder.person.bio.remote

internal data class LiveAiTestAuthorizationRequirement(
    val environmentName: String,
    val reason: String,
)

internal object LiveAiTestAuthorization {
    fun selectedProvider(environment: (String) -> String?): String {
        val wireValue = environment(LIVE_AI_PROVIDER_ENVIRONMENT_NAME)
        require(wireValue != null && wireValue == wireValue.trim()) {
            "$LIVE_AI_PROVIDER_ENVIRONMENT_NAME must be exactly openai, gemini, or anthropic"
        }
        return PROVIDERS_BY_WIRE_VALUE[wireValue]
            ?: throw IllegalArgumentException(
                "$LIVE_AI_PROVIDER_ENVIRONMENT_NAME must be exactly openai, gemini, or anthropic",
            )
    }

    fun requirements(provider: String): List<LiveAiTestAuthorizationRequirement> {
        require(PROVIDER_PATTERN.matches(provider)) {
            "Live AI authorization provider name is invalid"
        }
        return listOf(
            LiveAiTestAuthorizationRequirement(
                environmentName = "RUN_LIVE_AI_TESTS",
                reason = "Authorize potentially billable live provider calls",
            ),
            LiveAiTestAuthorizationRequirement(
                environmentName =
                    "${provider}_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED",
                reason =
                    "Approve provider retention, abuse monitoring, human review, " +
                        "and product-improvement use, as applicable, only for the fixed " +
                        "synthetic smoke fixtures and versioned aggregate evaluation corpus",
            ),
            LiveAiTestAuthorizationRequirement(
                environmentName = "LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED",
                reason = "Confirm automatic content telemetry is disabled",
            ),
            LiveAiTestAuthorizationRequirement(
                environmentName = "LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED",
                reason =
                    "Confirm the captured application-owned HTTP request is sufficient evidence",
            ),
        )
    }

    fun unmetRequirements(
        provider: String,
        environment: (String) -> String?,
    ): List<LiveAiTestAuthorizationRequirement> =
        requirements(provider).filter { requirement ->
            environment(requirement.environmentName) != "true"
        }

    private const val LIVE_AI_PROVIDER_ENVIRONMENT_NAME = "LIVE_AI_PROVIDER"
    private val PROVIDERS_BY_WIRE_VALUE =
        mapOf(
            "openai" to "OPENAI",
            "gemini" to "GEMINI",
            "anthropic" to "ANTHROPIC",
        )
    private val PROVIDER_PATTERN = Regex("[A-Z][A-Z0-9_]{0,31}")
}
