package com.persons.finder.person.bio

import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException
import java.time.Duration
import tools.jackson.databind.json.JsonMapper

class BioConfigurationTest {
    @Test
    fun `unknown bio adapter configuration fails instead of falling back`() {
        assertThrows(BeanCreationException::class.java) {
            configuredGenerator(generator = "network-or-unknown", runtimeMode = "test")
        }
    }

    @Test
    fun `deterministic adapter cannot be selected as a production default`() {
        assertThrows(BeanCreationException::class.java) {
            configuredGenerator(generator = "deterministic", runtimeMode = "production")
        }
    }

    @Test
    fun `remote adapter requires the explicitly networked runtime`() {
        assertThrows(BeanCreationException::class.java) {
            remoteGenerator(provider = "openai", runtimeMode = "assessment-local")
        }
    }

    @Test
    fun `remote adapter rejects unknown provider missing credential invalid model and unsafe timeout`() {
        listOf(
            { remoteGenerator(provider = "other") },
            { remoteGenerator(provider = "openai", openAiApiKey = "") },
            { remoteGenerator(provider = "gemini", geminiApiKey = "") },
            { remoteGenerator(provider = "anthropic", anthropicApiKey = "") },
            { remoteGenerator(provider = "openai", model = "../bad model") },
            {
                remoteGenerator(
                    provider = "openai",
                    timeout = Duration.ofMillis(999),
                )
            },
        ).forEach { configuration ->
            assertThrows(BeanCreationException::class.java) {
                configuration()
            }
        }
    }

    @Test
    fun `remote timeout rejects values above the application-owned deadline`() {
        assertThrows(BeanCreationException::class.java) {
            remoteGenerator(
                provider = "openai",
                timeout = BIO_GENERATION_DEADLINE.plusNanos(1),
            )
        }
    }

    @Test
    fun `remote timeout accepts the exact application-owned deadline`() {
        assertEquals(Duration.ofSeconds(15), BIO_GENERATION_DEADLINE)
        assertInstanceOf(
            RemoteBioGenerator::class.java,
            remoteGenerator(
                provider = "openai",
                timeout = BIO_GENERATION_DEADLINE,
            ),
        )
    }

    @Test
    fun `OpenAI Gemini and Anthropic can be selected behind the same remote adapter`() {
        assertInstanceOf(
            RemoteBioGenerator::class.java,
            remoteGenerator(provider = "openai"),
        )
        assertInstanceOf(
            RemoteBioGenerator::class.java,
            remoteGenerator(provider = "gemini"),
        )
        assertInstanceOf(
            RemoteBioGenerator::class.java,
            remoteGenerator(provider = "anthropic"),
        )
    }

    @Test
    fun `secure runtime requires an explicit valid selector at startup`() {
        listOf("", "deterministic", "unknown").forEach { selector ->
            assertThrows(BeanCreationException::class.java) {
                configuredGenerator(
                    generator = selector,
                    runtimeMode = "network-private",
                )
            }
        }
    }

    @Test
    fun `selected adapter is invoked once and no deterministic fallback runs after failure`() {
        var transportCalls = 0
        val transport =
            ProviderHttpTransport {
                transportCalls++
                ProviderHttpResponse(503, """{"error":"synthetic"}""")
            }
        val generator =
            BioConfiguration().bioGenerator(
                configuredGenerator = "remote",
                runtimeMode = "network-private",
                configuredProvider = "openai",
                configuredModel = "test-model",
                timeout = Duration.ofSeconds(5),
                openAiApiKey = "synthetic-credential",
                geminiApiKey = "",
                anthropicApiKey = "",
                objectMapper = JsonMapper.builder().build(),
                transport = transport,
            )

        assertEquals(
            BioGenerationResult.Failure(BioGenerationFailure.UNAVAILABLE),
            generator.generate(
                BioTemplateRequest(
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                ),
            ),
        )
        assertEquals(1, transportCalls)
    }

    private fun remoteGenerator(
        provider: String,
        runtimeMode: String = "network-private",
        model: String = "test-model",
        timeout: Duration = Duration.ofSeconds(5),
        openAiApiKey: String = "openai-test-credential",
        geminiApiKey: String = "gemini-test-credential",
        anthropicApiKey: String = "anthropic-test-credential",
    ): BioGenerator =
        configuredGenerator(
            generator = "remote",
            runtimeMode = runtimeMode,
            provider = provider,
            model = model,
            timeout = timeout,
            openAiApiKey = openAiApiKey,
            geminiApiKey = geminiApiKey,
            anthropicApiKey = anthropicApiKey,
        )

    private fun configuredGenerator(
        generator: String,
        runtimeMode: String,
        provider: String = "",
        model: String = "",
        timeout: Duration = Duration.ofSeconds(5),
        openAiApiKey: String = "",
        geminiApiKey: String = "",
        anthropicApiKey: String = "",
    ): BioGenerator =
        BioConfiguration().bioGenerator(
            configuredGenerator = generator,
            runtimeMode = runtimeMode,
            configuredProvider = provider,
            configuredModel = model,
            timeout = timeout,
            openAiApiKey = openAiApiKey,
            geminiApiKey = geminiApiKey,
            anthropicApiKey = anthropicApiKey,
            objectMapper = JsonMapper.builder().build(),
            transport = ProviderHttpTransport { error("not called during construction") },
        )
}
