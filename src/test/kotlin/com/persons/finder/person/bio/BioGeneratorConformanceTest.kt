package com.persons.finder.person.bio

import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.Duration
import java.util.concurrent.CancellationException
import tools.jackson.databind.json.JsonMapper

class BioGeneratorConformanceTest {
    @TestFactory
    fun `every shipped generator satisfies the application-owned template contract`(): List<DynamicTest> =
        conformingGenerators().map { (name, generator) ->
            DynamicTest.dynamicTest(name) {
                listOf(
                    BioTemplateRequest(
                        jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
                        interests = listOf(SafeInterestCode.OUTDOORS_NATURE),
                    ),
                    BioTemplateRequest(
                        jobCategory = SafeJobCode.OTHER,
                        interests = listOf(SafeInterestCode.OTHER),
                    ),
                ).forEach { request ->
                    val result =
                        generator.generate(
                            request,
                            BioGenerationContext.start(Duration.ofSeconds(1)),
                        )
                    assertTrue(result is BioGenerationResult.Template, "$name -> $result")
                    val template = (result as BioGenerationResult.Template).value
                    TemplateToken.entries.forEach { token ->
                        assertEquals(
                            1,
                            template.value.windowed(token.literal.length).count { it == token.literal },
                            "$name/${token.name}",
                        )
                    }
                    assertFalse(template.value.contains("North Island"))
                    assertFalse(template.value.contains("South Island"))
                    assertEquals(
                        BioGenerationResult.Template(template),
                        GeneratedBioTemplate.validate(template.value),
                    )
                }
            }
        }

    @Test
    fun `remote failure categories remain normalized with no fallback invocation`() {
        BioGenerationFailure.entries.forEach { failure ->
            var providerCalls = 0
            var fallbackCalls = 0
            val remote =
                RemoteBioGenerator(
                    ModelProviderClient {
                        providerCalls++
                        ModelProviderResult.Failure(failure)
                    },
                    JsonMapper.builder().build(),
                )
            val result = remote.generate(safeRequest())

            assertEquals(BioGenerationResult.Failure(failure), result)
            assertEquals(1, providerCalls)
            assertEquals(0, fallbackCalls)
        }
    }

    @Test
    fun `shared deadline and cancellation contract is adapter neutral`() {
        conformingGenerators().forEach { (name, generator) ->
            var now = 1L
            val expired = BioGenerationContext.start(Duration.ofNanos(1)) { now }
            now = 2L
            assertEquals(
                BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT),
                generator.generate(safeRequest(), expired),
                name,
            )
        }

        val cancelled = BioGenerationContext.start(Duration.ofSeconds(1))
        Thread.currentThread().interrupt()
        try {
            conformingGenerators().forEach { (_, generator) ->
                org.junit.jupiter.api.Assertions.assertThrows(CancellationException::class.java) {
                    generator.generate(safeRequest(), cancelled)
                }
            }
        } finally {
            Thread.interrupted()
        }
    }

    private fun conformingGenerators(): List<Pair<String, BioGenerator>> =
        listOf(
            "deterministic" to DeterministicBioGenerator(),
            "remote" to
                RemoteBioGenerator(
                    ModelProviderClient {
                        ModelProviderResult.Generated(
                            """{"template_id":"quirky_side_quest"}""",
                        )
                    },
                    JsonMapper.builder().build(),
                ),
        )

    private fun safeRequest() =
        BioTemplateRequest(
            jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
            interests = listOf(SafeInterestCode.OTHER),
        )
}
