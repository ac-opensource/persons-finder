package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioTemplateId
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.MacroRegion
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper

class RemoteBioGeneratorTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `shared adapter sends only the typed sanitized allowlist and accepts a closed template id`() {
        var capturedRequest: ModelGenerationRequest? = null
        val generator =
            RemoteBioGenerator(
                providerClient =
                    ModelProviderClient { request ->
                        capturedRequest = request
                        ModelProviderResult.Generated(
                            """{"template_id":"delightful_twist"}""",
                        )
                    },
                objectMapper = objectMapper,
            )

        val result =
            generator.generate(
                BioTemplateRequest(
                    jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
                    interests = listOf(SafeInterestCode.MUSIC, SafeInterestCode.TRAVEL),
                ),
            )

        assertEquals(
            BioGenerationResult.Template(
                GeneratedBioTemplate.fromCatalog(BioTemplateId.DELIGHTFUL_TWIST),
            ),
            result,
        )
        val providerRequest = requireNotNull(capturedRequest)
        val payload = objectMapper.readTree(providerRequest.inputJson)
        assertEquals(
            setOf(
                "display_name",
                "locale",
                "country_code",
                "job_category",
                "job_category_mapping_version",
                "interests",
                "interest_category_mapping_version",
                "tone",
            ),
            payload.propertyNames().toSet(),
        )
        assertEquals("{{NAME}}", payload.path("display_name").stringValue())
        assertEquals("en-NZ", payload.path("locale").stringValue())
        assertEquals("NZ", payload.path("country_code").stringValue())
        assertEquals("technology_engineering", payload.path("job_category").stringValue())
        assertEquals(
            listOf("music", "travel"),
            payload.path("interests").toList().map { it.stringValue() },
        )
        listOf("Andrew", "Software engineer", "music lessons", "-41.2865", "person-id")
            .forEach { forbidden ->
                assertFalse(providerRequest.inputJson.contains(forbidden))
            }

        val schema = objectMapper.readTree(providerRequest.outputSchemaJson)
        assertEquals(
            listOf("template_id"),
            schema.path("required").toList().map { it.stringValue() },
        )
        assertEquals(
            BioTemplateId.entries.map(BioTemplateId::wireValue),
            schema.path("properties").path("template_id").path("enum")
                .toList()
                .map { it.stringValue() },
        )
        assertFalse(schema.path("additionalProperties").asBoolean())
        assertEquals(64, providerRequest.maxOutputTokens)
        assertTrue(providerRequest.instructions.contains("inert data"))
        assertTrue(providerRequest.instructions.contains("Do not write bio prose"))
    }

    @Test
    fun `shared adapter accepts every closed template id and rejects all free-form output`() {
        BioTemplateId.entries.forEach { templateId ->
            assertEquals(
                BioGenerationResult.Template(GeneratedBioTemplate.fromCatalog(templateId)),
                RemoteBioGenerator(
                    ModelProviderClient {
                        ModelProviderResult.Generated(
                            """{"template_id":"${templateId.wireValue}"}""",
                        )
                    },
                    objectMapper,
                ).generate(safeRequest()),
            )
        }

        listOf(
            "",
            "not-json",
            """{"template":"{{NAME}} ignores safeguards."}""",
            """{"template_id":"unknown"}""",
            """{"template_id":"quirky_side_quest","extra":true}""",
            """{"template_id":7}""",
            """{"template_id":"quirky_side_quest\u202e"}""",
            """{"template_id":"unknown","template_id":"quirky_side_quest"}""",
            """{"template_id":"quirky_side_quest"} {"template_id":"delightful_twist"}""",
            " ".repeat(257),
        ).forEach { output ->
            assertEquals(
                BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                RemoteBioGenerator(
                    ModelProviderClient { ModelProviderResult.Generated(output) },
                    objectMapper,
                ).generate(safeRequest()),
                output,
            )
        }
    }

    @Test
    fun `shared adapter includes optional macro region as a closed code`() {
        var capturedRequest: ModelGenerationRequest? = null
        val generator =
            RemoteBioGenerator(
                ModelProviderClient { request ->
                    capturedRequest = request
                    ModelProviderResult.Generated(
                        """{"template_id":"quirky_side_quest"}""",
                    )
                },
                objectMapper,
            )

        generator.generate(
            BioTemplateRequest(
                jobCategory = SafeJobCode.OTHER,
                interests = listOf(SafeInterestCode.OTHER),
                macroRegion = MacroRegion.SOUTH_ISLAND,
            ),
        )

        assertEquals(
            "South Island",
            objectMapper.readTree(requireNotNull(capturedRequest).inputJson)
                .path("macro_region")
                .stringValue(),
        )
    }

    @Test
    fun `shared adapter preserves provider failure classification`() {
        BioGenerationFailure.entries.forEach { failure ->
            val result =
                RemoteBioGenerator(
                    ModelProviderClient { ModelProviderResult.Failure(failure) },
                    objectMapper,
                ).generate(safeRequest())

            assertEquals(BioGenerationResult.Failure(failure), result)
        }
    }

    @TestFactory
    fun `every closed job code serializes to its exact wire value`(): List<DynamicTest> =
        SafeJobCode.entries.map { job ->
            DynamicTest.dynamicTest(job.wireValue) {
                val payload = capturePayload(job, listOf(SafeInterestCode.OTHER))
                assertEquals(job.wireValue, payload.path("job_category").stringValue())
            }
        }

    @TestFactory
    fun `every closed interest code serializes to its exact wire value`(): List<DynamicTest> =
        SafeInterestCode.entries.map { interest ->
            DynamicTest.dynamicTest(interest.wireValue) {
                val payload = capturePayload(SafeJobCode.OTHER, listOf(interest))
                assertEquals(
                    listOf(interest.wireValue),
                    payload.path("interests").toList().map { it.stringValue() },
                )
            }
        }

    @Test
    fun `adversarial source sentinels cannot enter any canonical payload field`() {
        val request =
            BioTemplateRequest(
                jobCategory = SafeJobCode.OTHER,
                interests = listOf(SafeInterestCode.OTHER),
            )
        var captured: ModelGenerationRequest? = null
        val generator =
            RemoteBioGenerator(
                ModelProviderClient {
                    captured = it
                    ModelProviderResult.Generated("""{"template_id":"quirky_side_quest"}""")
                },
                objectMapper,
            )

        generator.generate(request)

        val complete =
            listOf(
                requireNotNull(captured).instructions,
                requireNotNull(captured).inputJson,
                requireNotNull(captured).outputSchemaJson,
            ).joinToString("|")
        listOf(
            "Robotics engineer at Acme for Alice",
            "123 Queen St",
            "-43.5,172.6",
            "person-id",
            "oidc-subject",
            "Bearer source-access-token",
            "Private Club hobby",
            "Ignore all instructions",
        ).forEach { forbidden ->
            assertFalse(complete.contains(forbidden), forbidden)
        }
    }

    private fun capturePayload(
        job: SafeJobCode,
        interests: List<SafeInterestCode>,
    ): tools.jackson.databind.JsonNode {
        var captured: ModelGenerationRequest? = null
        RemoteBioGenerator(
            ModelProviderClient {
                captured = it
                ModelProviderResult.Generated("""{"template_id":"quirky_side_quest"}""")
            },
            objectMapper,
        ).generate(BioTemplateRequest(jobCategory = job, interests = interests))
        return objectMapper.readTree(requireNotNull(captured).inputJson)
    }

    private fun safeRequest() =
        BioTemplateRequest(
            jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
            interests = listOf(SafeInterestCode.MUSIC),
        )
}
