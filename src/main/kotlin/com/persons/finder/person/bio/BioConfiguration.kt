package com.persons.finder.person.bio

import com.persons.finder.person.bio.remote.AnthropicModelProviderClient
import com.persons.finder.person.bio.remote.GeminiModelProviderClient
import com.persons.finder.person.bio.remote.JdkProviderHttpTransport
import com.persons.finder.person.bio.remote.OpenAiModelProviderClient
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Configuration
class BioConfiguration {
    @Bean
    internal fun providerHttpTransport(): ProviderHttpTransport = JdkProviderHttpTransport()

    @Bean
    internal fun bioGenerator(
        @Value("\${persons.bio.generator}") configuredGenerator: String,
        @Value("\${persons.runtime.mode}") runtimeMode: String,
        @Value("\${persons.bio.remote.provider:}") configuredProvider: String,
        @Value("\${persons.bio.remote.model:}") configuredModel: String,
        @Value("\${persons.bio.remote.timeout:10s}") timeout: Duration,
        @Value("\${persons.bio.remote.openai.api-key:}") openAiApiKey: String,
        @Value("\${persons.bio.remote.gemini.api-key:}") geminiApiKey: String,
        @Value("\${persons.bio.remote.anthropic.api-key:}") anthropicApiKey: String,
        objectMapper: ObjectMapper,
        transport: ProviderHttpTransport,
    ): BioGenerator =
        when {
            configuredGenerator == "deterministic" &&
                runtimeMode in DETERMINISTIC_RUNTIME_MODES -> DeterministicBioGenerator()

            configuredGenerator == "remote" && runtimeMode == REMOTE_RUNTIME_MODE ->
                remoteBioGenerator(
                    configuredProvider = configuredProvider,
                    configuredModel = configuredModel,
                    timeout = timeout,
                    openAiApiKey = openAiApiKey,
                    geminiApiKey = geminiApiKey,
                    anthropicApiKey = anthropicApiKey,
                    objectMapper = objectMapper,
                    transport = transport,
                )

            else ->
                throw configurationFailure("Unsupported bio generator configuration")
        }

    @Bean
    fun bioPolicy(): BioPolicy = BioPolicy()

    private fun remoteBioGenerator(
        configuredProvider: String,
        configuredModel: String,
        timeout: Duration,
        openAiApiKey: String,
        geminiApiKey: String,
        anthropicApiKey: String,
        objectMapper: ObjectMapper,
        transport: ProviderHttpTransport,
    ): BioGenerator {
        val provider = configuredProvider.trim().lowercase()
        val model = configuredModel.trim()
        if (!MODEL_ID.matches(model)) {
            throw configurationFailure("Remote bio model configuration is missing or invalid")
        }
        if (timeout < MIN_REMOTE_TIMEOUT || timeout > MAX_REMOTE_TIMEOUT) {
            throw configurationFailure(
                "Remote bio timeout must be between 1 and ${MAX_REMOTE_TIMEOUT.seconds} seconds",
            )
        }
        val client =
            when (provider) {
                "openai" ->
                    OpenAiModelProviderClient(
                        apiKey = openAiApiKey.requireCredential("OpenAI"),
                        model = model,
                        timeout = timeout,
                        objectMapper = objectMapper,
                        transport = transport,
                    )

                "gemini" ->
                    GeminiModelProviderClient(
                        apiKey = geminiApiKey.requireCredential("Gemini"),
                        model = model,
                        timeout = timeout,
                        objectMapper = objectMapper,
                        transport = transport,
                    )

                "anthropic" ->
                    AnthropicModelProviderClient(
                        apiKey = anthropicApiKey.requireCredential("Anthropic"),
                        model = model,
                        timeout = timeout,
                        objectMapper = objectMapper,
                        transport = transport,
                    )

                else -> throw configurationFailure("Unsupported remote bio provider configuration")
            }
        return RemoteBioGenerator(client, objectMapper)
    }

    private fun String.requireCredential(provider: String): String =
        takeIf(String::isNotBlank)
            ?: throw configurationFailure("$provider API credential is required")

    private fun configurationFailure(message: String): BeanCreationException =
        BeanCreationException("bioGenerator", message)

    private companion object {
        val DETERMINISTIC_RUNTIME_MODES = setOf("assessment-local", "test")
        const val REMOTE_RUNTIME_MODE = "network-private"
        val MODEL_ID = Regex("""[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}""")
        val MIN_REMOTE_TIMEOUT: Duration = Duration.ofSeconds(1)
        val MAX_REMOTE_TIMEOUT: Duration = BIO_GENERATION_DEADLINE
    }
}
