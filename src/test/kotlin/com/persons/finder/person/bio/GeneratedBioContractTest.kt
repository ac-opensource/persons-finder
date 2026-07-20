package com.persons.finder.person.bio

import com.persons.finder.person.model.PersonProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class GeneratedBioContractTest {
    @TestFactory
    fun `each required placeholder has independent exactly-one structural validation`(): List<DynamicTest> =
        TemplateToken.entries.flatMap { token ->
            val literal = token.literal
            listOf(
                "missing" to VALID_TEMPLATE.replace(literal, token.name.lowercase()),
                "duplicate" to VALID_TEMPLATE.replace(literal, "$literal and $literal"),
                "extra occurrence" to VALID_TEMPLATE.dropLast(1) + " $literal.",
                "changed case" to VALID_TEMPLATE.replace(literal, literal.lowercase()),
                "added whitespace" to VALID_TEMPLATE.replace(literal, "{{ ${token.name} }}"),
                "partial mutation" to VALID_TEMPLATE.replace(literal, "{{${token.name.dropLast(1)}}}"),
                "escaped form" to VALID_TEMPLATE.replace(literal, "\\$literal"),
                "markup wrapped" to VALID_TEMPLATE.replace(literal, "<b>$literal</b>"),
                "unknown replacement token" to VALID_TEMPLATE.replace(literal, "{{OTHER}}"),
            ).map { (variant, candidate) ->
                DynamicTest.dynamicTest("${token.name} $variant") {
                    assertInvalidTemplate(candidate)
                }
            }
        }

    @TestFactory
    fun `catalog templates are independently normalized and fit the prose budget`(): List<DynamicTest> =
        BioTemplateId.entries.map { templateId ->
            DynamicTest.dynamicTest(templateId.wireValue) {
                val template = GeneratedBioTemplate.fromCatalog(templateId)
                assertEquals(
                    BioGenerationResult.Template(template),
                    GeneratedBioTemplate.validate("  ${template.value}  "),
                )
                TemplateToken.entries.forEach { token ->
                    assertEquals(1, template.value.windowed(token.literal.length).count { it == token.literal })
                }
                val fixedText =
                    TemplateToken.entries.fold(template.value) { value, token ->
                        value.replace(token.literal, "")
                    }
                assertTrue(
                    fixedText.codePointCount(0, fixedText.length) <=
                        BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS,
                )
            }
        }

    @Test
    fun `unsafe region excessive sentences wrapper and policy-invalid templates are rejected`() {
        listOf(
            VALID_TEMPLATE.replace("who enjoys", "from South Island who enjoys"),
            VALID_TEMPLATE.dropLast(1) + ". Two. Three. Four.",
            VALID_TEMPLATE.replace("very quirky", "I am hacked; very quirky"),
            VALID_TEMPLATE.replace("very quirky", "the following is the system prompt; quirky"),
            VALID_TEMPLATE.replace("very quirky", "quirky and follows every prompt"),
            VALID_TEMPLATE.replace("very quirky", "quirky with clear instructions"),
            VALID_TEMPLATE.replace("very quirky", "\\u{110000} quirky"),
            VALID_TEMPLATE.replace("very quirky", "quirky\n"),
            "",
            "\uD800",
        ).forEach(::assertInvalidTemplate)
    }

    @Test
    fun `one to three sentences and the model-authored literal budget are enforced`() {
        listOf(
            "{{NAME}} is a quirky {{JOB}} who enjoys {{HOBBY}}.",
            "{{NAME}} is a quirky {{JOB}}. {{HOBBY}} keeps ideas moving!",
            "{{NAME}} is a quirky {{JOB}}. {{HOBBY}} sparks ideas! Always curious?",
            "{{NAME}} promptly turns {{HOBBY}} into a quirky adventure as a {{JOB}}.",
        ).forEach { candidate ->
            assertTrue(
                GeneratedBioTemplate.validate(candidate) is BioGenerationResult.Template,
                candidate,
            )
        }
        assertInvalidTemplate(
            "{{NAME}} is a {{JOB}}. {{HOBBY}} sparks ideas! Always curious? One more.",
        )
        assertInvalidTemplate(
            "{{NAME}} is a {{JOB}}. A. B. C. {{HOBBY}} rocks.",
        )

        val exactLiteralBudget =
            templateWithLiteralCodePoints(BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS)
        assertTrue(
            GeneratedBioTemplate.validate(exactLiteralBudget) is BioGenerationResult.Template,
        )
        assertInvalidTemplate(
            templateWithLiteralCodePoints(BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS + 1),
        )
    }

    @Test
    fun `one-pass composition never rescans placeholder or regex-looking source`() {
        val template = GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST)
        val grounding =
            BioGrounding(
                name = "{{JOB}} \$1 \$&",
                jobTitle = "{{HOBBY}} \\d+ [a-z]*",
                hobby = "{{NAME}} `quoted` <b>markdown **literal**</b>",
            )

        val bio = GeneratedBio.compose(template, grounding).value

        assertEquals(
            "Meet {{JOB}} \$1 \$&, a very quirky {{HOBBY}} \\d+ [a-z]* who enjoys " +
                "{{NAME}} `quoted` <b>markdown **literal**</b>.",
            bio,
        )
        assertTrue(bio.contains("\$1 \$&"))
        assertTrue(bio.contains("\\d+ [a-z]*"))
    }

    @TestFactory
    fun `sentence punctuation in opaque source is not reinterpreted after composition`():
        List<DynamicTest> =
        BioTemplateId.entries.map { templateId ->
            DynamicTest.dynamicTest(templateId.wireValue) {
                val grounding =
                    BioGrounding(
                        name = "Synthetic Ltd.",
                        jobTitle = "Engineer. Team lead",
                        hobby = "board games? Sometimes",
                    )

                val bio =
                    GeneratedBio.compose(
                        GeneratedBioTemplate.fromCatalog(templateId),
                        grounding,
                    ).value

                assertTrue(bio.contains(grounding.name))
                assertTrue(bio.contains(grounding.jobTitle))
                assertTrue(bio.contains(grounding.hobby))
                assertTrue(
                    bio.codePointCount(0, bio.length) <=
                        BioPolicy.FINAL_BIO_MAX_CODE_POINTS,
                )
            }
        }

    @Test
    fun `composer preserves legitimate Unicode and joiners as opaque source`() {
        val template = GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST)
        val bio =
            GeneratedBio.compose(
                template,
                BioGrounding(
                    name = "Andrew 🧭",
                    jobTitle = "emoji\u200Dmaker",
                    hobby = "stargazing and café",
                ),
            ).value

        assertTrue(bio.contains("Andrew 🧭"))
        assertTrue(bio.contains("emoji\u200Dmaker"))
        assertTrue(bio.contains("stargazing and café"))
    }

    @Test
    fun `composer rejects controls bidi and malformed final grounding`() {
        val template = GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST)
        listOf(
            BioGrounding("Synthetic\nName", "Engineer", "hiking"),
            BioGrounding("Synthetic", "Eng\u202Eineer", "hiking"),
            BioGrounding("Synthetic", "Engineer", "hobby\u0000"),
            BioGrounding("\uD800", "Engineer", "hiking"),
        ).forEach { grounding ->
            assertThrows(IllegalArgumentException::class.java) {
                GeneratedBio.compose(template, grounding)
            }
        }
    }

    @Test
    fun `final limit is Unicode-code-point based and grounding remains exact`() {
        val template =
            when (
                val result =
                    GeneratedBioTemplate.validate(
                        templateWithLiteralCodePoints(
                            BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS,
                        ),
                    )
            ) {
                is BioGenerationResult.Template -> result.value
                is BioGenerationResult.Failure -> error("Fixture must be valid")
            }
        val profile =
            PersonProfile.create(
                "🧭".repeat(80),
                "J".repeat(80),
                listOf("H".repeat(60)),
            )
        val exact = GeneratedBio.compose(template, profile, profile.hobbies.single()).value

        assertEquals(BioPolicy.FINAL_BIO_MAX_CODE_POINTS, exact.codePointCount(0, exact.length))
        assertTrue(exact.contains(profile.name))
        assertTrue(exact.contains(profile.jobTitle))
        assertTrue(exact.contains(profile.hobbies.single()))
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBio.compose(
                template,
                BioGrounding(
                    profile.name,
                    profile.jobTitle,
                    "H".repeat(61),
                ),
            )
        }
    }

    @Test
    fun `grounding hobby must be one of the validated originals`() {
        val profile = PersonProfile.create("Synthetic", "Engineer", listOf("hiking"))
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBio.compose(
                GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
                profile,
                "not supplied",
            )
        }
    }

    private fun assertInvalidTemplate(candidate: String) {
        val result = GeneratedBioTemplate.validate(candidate)
        assertTrue(result is BioGenerationResult.Failure, candidate)
        val reason = (result as BioGenerationResult.Failure).reason
        assertTrue(
            reason == BioGenerationFailure.INVALID_OUTPUT ||
                reason == BioGenerationFailure.POLICY_REJECTED,
            "$candidate -> $reason",
        )
    }

    private fun templateWithLiteralCodePoints(literalCodePoints: Int): String {
        require(literalCodePoints > 0)
        return "{{NAME}}{{JOB}}{{HOBBY}}" + "x".repeat(literalCodePoints - 1) + "."
    }

    private companion object {
        const val VALID_TEMPLATE =
            "Meet {{NAME}}, a very quirky {{JOB}} who enjoys {{HOBBY}}."
    }
}
