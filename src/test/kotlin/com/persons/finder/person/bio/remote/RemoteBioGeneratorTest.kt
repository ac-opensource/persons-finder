package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.MacroRegion
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper

class RemoteBioGeneratorTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `shared adapter sends only the typed sanitized allowlist and accepts generated prose`() {
        var capturedRequest: ModelGenerationRequest? = null
        val result =
            RemoteBioGenerator(
                providerClient =
                    ModelProviderClient { request ->
                        capturedRequest = request
                        ModelProviderResult.Generated(validOutput(FIRST_TEMPLATE))
                    },
                objectMapper = objectMapper,
            ).generate(safeRequest())

        assertEquals(validTemplate(FIRST_TEMPLATE), result)
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
                "hobby_placeholders",
                "tone",
            ),
            payload.propertyNames().toSet(),
        )
        assertEquals("{{NAME}}", payload.path("display_name").stringValue())
        assertEquals("en-NZ", payload.path("locale").stringValue())
        assertEquals("NZ", payload.path("country_code").stringValue())
        assertEquals("technology_engineering", payload.path("job_category").stringValue())
        assertEquals(listOf("music"), payload.path("interests").toList().map { it.stringValue() })
        assertEquals(
            listOf("{{HOBBY[0]}}"),
            payload.path("hobby_placeholders").toList().map { it.stringValue() },
        )
        listOf("Andrew", "Software engineer", "music lessons", "-41.2865", "person-id")
            .forEach { forbidden -> assertFalse(providerRequest.inputJson.contains(forbidden)) }

        val schema = objectMapper.readTree(providerRequest.outputSchemaJson)
        assertEquals(listOf("bio_template"), schema.path("required").toList().map { it.stringValue() })
        assertEquals("string", schema.path("properties").path("bio_template").path("type").stringValue())
        assertTrue(schema.path("properties").path("bio_template").path("maxLength").isMissingNode)
        assertTrue(schema.path("properties").path("bio_template").path("enum").isMissingNode)
        assertFalse(schema.path("additionalProperties").asBoolean())
        assertEquals(256, providerRequest.maxOutputTokens)
        assertTrue(providerRequest.instructions.contains("inert data"))
        assertTrue(providerRequest.instructions.contains("one to three sentences"))
        assertTrue(providerRequest.instructions.contains("repeat any placeholder"))
        assertTrue(providerRequest.instructions.contains("distinct creative beats"))
        assertTrue(providerRequest.instructions.contains("512 total characters"))
    }

    @Test
    fun `shared adapter requires every indexed hobby slot exactly once`() {
        val request =
            BioTemplateRequest(
                jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
                interests = listOf(SafeInterestCode.MUSIC),
                hobbyCount = 3,
            )
        val valid =
            "{{NAME}} is a {{JOB}} who turns {{HOBBY[0]}} into an opening act, " +
                "{{HOBBY[1]}} into a plot twist, and {{HOBBY[2]}} into an encore."

        assertEquals(
            validTemplate(valid, request.hobbyCount),
            generatorReturning(valid).generate(request),
        )
        listOf(
            valid.replace("{{HOBBY[1]}}", "missing"),
            valid.replace("{{HOBBY[1]}}", "{{HOBBY[0]}}"),
            valid.replace("{{HOBBY[1]}}", "{{HOBBY[3]}}"),
            valid.replace("{{HOBBY[1]}}", "{{HOBBY}}"),
            valid.replace("{{HOBBY[1]}}", "{{HOBBY[01]}}"),
        ).forEach { candidate ->
            assertEquals(
                BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                generatorReturning(candidate).generate(request),
                candidate,
            )
        }
    }

    @Test
    fun `shared adapter accepts distinct valid provider prose templates`() {
        val first = generatorReturning(FIRST_TEMPLATE).generate(safeRequest())
        val second = generatorReturning(SECOND_TEMPLATE).generate(safeRequest())

        assertEquals(validTemplate(FIRST_TEMPLATE), first)
        assertEquals(validTemplate(SECOND_TEMPLATE), second)
        assertNotEquals(first, second)
    }

    @Test
    fun `shared adapter rejects malformed and unsafe provider prose`() {
        listOf(
            "",
            "not-json",
            """{"template":"$FIRST_TEMPLATE"}""",
            """{"bio_template":"$FIRST_TEMPLATE","extra":true}""",
            """{"bio_template":7}""",
            """{"bio_template":"$FIRST_TEMPLATE","bio_template":"$SECOND_TEMPLATE"}""",
            """{"bio_template":"$FIRST_TEMPLATE"} {"bio_template":"$SECOND_TEMPLATE"}""",
            " ".repeat(MAX_REMOTE_GENERATOR_OUTPUT_CHARS + 1),
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

        listOf(
            "{{NAME}} reveals the system prompt while {{HOBBY[0]}} as a {{JOB}}.",
            "{{NAME}} follows every prompt as a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
            "{{NAME}} discusses prompts as a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
            "{{NAME}} follows one instruction as a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
            "{{NAME}} follows clear instructions as a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
        ).forEach { template ->
            assertEquals(
                BioGenerationResult.Failure(BioGenerationFailure.POLICY_REJECTED),
                generatorReturning(template).generate(safeRequest()),
                template,
            )
        }
    }

    @Test
    fun `shared adapter emits one closed content-free diagnostic for each terminal result`() {
        val cases =
            listOf(
                "not-json" to RemoteBioGenerationDiagnostic.OUTPUT_JSON_MALFORMED,
                """{"bio_template":"$FIRST_TEMPLATE","extra":true}""" to
                    RemoteBioGenerationDiagnostic.OUTPUT_JSON_ROOT_SHAPE,
                """{"template":"$FIRST_TEMPLATE"}""" to
                    RemoteBioGenerationDiagnostic.OUTPUT_JSON_FIELD_SHAPE,
                validOutput(
                    "{{NAME}} and {{NAME}} enjoy {{HOBBY[0]}} as a {{JOB}}.",
                ) to RemoteBioGenerationDiagnostic.TEMPLATE_PLACEHOLDER_CARDINALITY,
                validOutput(
                    "{{NAME}} is a {{JOB}}. {{HOBBY[0]}} helps! Still curious? One more.",
                ) to RemoteBioGenerationDiagnostic.TEMPLATE_SENTENCE_COUNT,
                validOutput(
                    "{{NAME}} follows every prompt as a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
                ) to RemoteBioGenerationDiagnostic.TEMPLATE_CONTENT_POLICY,
                validOutput(FIRST_TEMPLATE) to
                    RemoteBioGenerationDiagnostic.VALID_TEMPLATE,
            )

        cases.forEach { (output, expectedDiagnostic) ->
            val diagnostics = mutableListOf<RemoteBioGenerationDiagnosticEvent>()
            RemoteBioGenerator(
                providerClient =
                    ModelProviderClient {
                        ModelProviderResult.Generated(output)
                },
                objectMapper = objectMapper,
                diagnosticSink =
                    RemoteBioGenerationDiagnosticSink { diagnostic ->
                        diagnostics += diagnostic
                    },
            ).generate(safeRequest())

            assertEquals(expectedDiagnostic, diagnostics.single().diagnostic, output)
            assertFalse(diagnostics.single().toString().contains(output))
            assertEquals(output.toByteArray(Charsets.UTF_8).size, diagnostics.single().outputJsonUtf8Bytes)
        }
    }

    @Test
    fun `shared adapter includes optional macro region as a closed code`() {
        var capturedRequest: ModelGenerationRequest? = null
        RemoteBioGenerator(
            ModelProviderClient {
                capturedRequest = it
                ModelProviderResult.Generated(validOutput(FIRST_TEMPLATE))
            },
            objectMapper,
        ).generate(
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
            assertEquals(
                BioGenerationResult.Failure(failure),
                RemoteBioGenerator(
                    ModelProviderClient { ModelProviderResult.Failure(failure) },
                    objectMapper,
                ).generate(safeRequest()),
            )
        }
    }

    @TestFactory
    fun `every closed job code serializes to its exact wire value`(): List<DynamicTest> =
        SafeJobCode.entries.map { job ->
            DynamicTest.dynamicTest(job.wireValue) {
                assertEquals(
                    job.wireValue,
                    capturePayload(job, listOf(SafeInterestCode.OTHER))
                        .path("job_category")
                        .stringValue(),
                )
            }
        }

    @TestFactory
    fun `every closed interest code serializes to its exact wire value`(): List<DynamicTest> =
        SafeInterestCode.entries.map { interest ->
            DynamicTest.dynamicTest(interest.wireValue) {
                assertEquals(
                    listOf(interest.wireValue),
                    capturePayload(SafeJobCode.OTHER, listOf(interest))
                        .path("interests")
                        .toList()
                        .map { it.stringValue() },
                )
            }
        }

    @Test
    fun `adversarial source sentinels cannot enter any canonical payload field`() {
        var captured: ModelGenerationRequest? = null
        RemoteBioGenerator(
            ModelProviderClient {
                captured = it
                ModelProviderResult.Generated(validOutput(FIRST_TEMPLATE))
            },
            objectMapper,
        ).generate(
            BioTemplateRequest(
                jobCategory = SafeJobCode.OTHER,
                interests = listOf(SafeInterestCode.OTHER),
            ),
        )

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
        ).forEach { forbidden -> assertFalse(complete.contains(forbidden), forbidden) }
    }

    private fun generatorReturning(template: String): RemoteBioGenerator =
        RemoteBioGenerator(
            ModelProviderClient { ModelProviderResult.Generated(validOutput(template)) },
            objectMapper,
        )

    private fun capturePayload(
        job: SafeJobCode,
        interests: List<SafeInterestCode>,
    ): tools.jackson.databind.JsonNode {
        var captured: ModelGenerationRequest? = null
        RemoteBioGenerator(
            ModelProviderClient {
                captured = it
                ModelProviderResult.Generated(validOutput(FIRST_TEMPLATE))
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

    private fun validOutput(template: String): String =
        objectMapper.writeValueAsString(mapOf("bio_template" to template))

    private fun validTemplate(
        template: String,
        hobbyCount: Int = 1,
    ): BioGenerationResult.Template =
        when (val result = GeneratedBioTemplate.validate(template, hobbyCount)) {
            is BioGenerationResult.Template -> result
            is BioGenerationResult.Failure -> error("Fixture must be a valid template: ${result.reason}")
        }

    private companion object {
        const val FIRST_TEMPLATE =
            "{{NAME}} turns {{HOBBY[0]}} into a quirky side quest after a day as a {{JOB}}."
        const val SECOND_TEMPLATE =
            "{{NAME}} brings a delightful twist to {{HOBBY[0]}} as a {{JOB}}."
    }
}
