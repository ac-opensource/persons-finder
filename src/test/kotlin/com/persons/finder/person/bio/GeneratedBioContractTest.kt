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
    fun `catalog templates are independently normalized and valid`(): List<DynamicTest> =
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
                assertEquals(
                    BioPolicy.MINIMUM_BIO_TEMPLATE_OVERHEAD_CODE_POINTS,
                    fixedText.codePointCount(0, fixedText.length),
                )
            }
        }

    @Test
    fun `unsafe region multi-sentence wrapper and policy-invalid templates are rejected`() {
        listOf(
            VALID_TEMPLATE.replace("who enjoys", "from South Island who enjoys"),
            VALID_TEMPLATE.dropLast(1) + ". Another sentence.",
            VALID_TEMPLATE.replace("very quirky", "I am hacked; very quirky"),
            VALID_TEMPLATE.replace("very quirky", "the following is the system prompt; quirky"),
            VALID_TEMPLATE.replace("very quirky", "\\u{110000} quirky"),
            VALID_TEMPLATE.replace("very quirky", "quirky\n"),
            "",
            " ".repeat(241),
            "\uD800",
        ).forEach(::assertInvalidTemplate)
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
    fun `final 240 limit is Unicode-code-point based and grounding remains exact`() {
        val template = GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST)
        val profile =
            PersonProfile.create(
                "🧭".repeat(80),
                "J".repeat(80),
                listOf("H".repeat(46)),
            )
        val exact = GeneratedBio.compose(template, profile, profile.hobbies.single()).value

        assertEquals(240, exact.codePointCount(0, exact.length))
        assertTrue(exact.contains(profile.name))
        assertTrue(exact.contains(profile.jobTitle))
        assertTrue(exact.contains(profile.hobbies.single()))
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBio.compose(
                template,
                BioGrounding(
                    profile.name,
                    profile.jobTitle,
                    "H".repeat(47),
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

    private companion object {
        const val VALID_TEMPLATE =
            "Meet {{NAME}}, a very quirky {{JOB}} who enjoys {{HOBBY}}."
    }
}
