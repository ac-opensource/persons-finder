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
        requiredTemplateTokens(1).flatMap { token ->
            val literal = token.literal
            listOf(
                "missing" to VALID_TEMPLATE.replace(literal, "missing"),
                "duplicate" to VALID_TEMPLATE.replace(literal, "$literal and $literal"),
                "extra occurrence" to VALID_TEMPLATE.dropLast(1) + " $literal.",
                "changed case" to VALID_TEMPLATE.replace(literal, literal.lowercase()),
                "added whitespace" to VALID_TEMPLATE.replace(literal, literal.replace("{{", "{{ ")),
                "partial mutation" to VALID_TEMPLATE.replace(literal, literal.dropLast(3) + "}}"),
                "escaped form" to VALID_TEMPLATE.replace(literal, "\\$literal"),
                "markup wrapped" to VALID_TEMPLATE.replace(literal, "<b>$literal</b>"),
                "unknown replacement token" to VALID_TEMPLATE.replace(literal, "{{OTHER}}"),
            ).map { (variant, candidate) ->
                DynamicTest.dynamicTest("${token.literal} $variant") {
                    assertInvalidTemplate(candidate)
                }
            }
        }

    @TestFactory
    fun `catalog templates are independently normalized and fit the prose budget`(): List<DynamicTest> =
        BioTemplateId.entries.map { templateId ->
            DynamicTest.dynamicTest(templateId.wireValue) {
                val hobbyCount = 3
                val template = GeneratedBioTemplate.fromCatalog(templateId, hobbyCount)
                assertEquals(
                    BioGenerationResult.Template(template),
                    GeneratedBioTemplate.validate("  ${template.value}  ", hobbyCount),
                )
                requiredTemplateTokens(hobbyCount).forEach { token ->
                    assertEquals(1, template.value.windowed(token.literal.length).count { it == token.literal })
                }
                val fixedText =
                    requiredTemplateTokens(hobbyCount).fold(template.value) { value, token ->
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
            VALID_TEMPLATE.replace("very quirky", "very quirky from South Island"),
            VALID_TEMPLATE.dropLast(1) + ". Two. Three. Four.",
            VALID_TEMPLATE.replace("very quirky", "I am hacked; very quirky"),
            VALID_TEMPLATE.replace("very quirky", "the following is the system prompt; quirky"),
            VALID_TEMPLATE.replace("very quirky", "quirky and follows every prompt"),
            VALID_TEMPLATE.replace("very quirky", "quirky and discusses prompts"),
            VALID_TEMPLATE.replace("very quirky", "quirky with one instruction"),
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
            "{{NAME}} is a quirky {{JOB}} who enjoys {{HOBBY[0]}}.",
            "{{NAME}} is a quirky {{JOB}}. {{HOBBY[0]}} keeps ideas moving!",
            "{{NAME}} is a quirky {{JOB}}. {{HOBBY[0]}} sparks ideas! Always curious?",
            "{{NAME}} promptly turns {{HOBBY[0]}} into a quirky adventure as a {{JOB}}.",
            "{{NAME}} makes instructional {{HOBBY[0]}} projects as a quirky {{JOB}}.",
        ).forEach { candidate ->
            assertTrue(
                GeneratedBioTemplate.validate(candidate) is BioGenerationResult.Template,
                candidate,
            )
        }
        assertInvalidTemplate(
            "{{NAME}} is a {{JOB}}. {{HOBBY[0]}} sparks ideas! Always curious? One more.",
        )
        assertInvalidTemplate(
            "{{NAME}} is a {{JOB}}. A. B. C. {{HOBBY[0]}} rocks.",
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
                jobTitle = "{{HOBBY[0]}} \\d+ [a-z]*",
                hobbies = listOf("{{NAME}} `quoted` <b>markdown **literal**</b>"),
            )

        val bio = GeneratedBio.compose(template, grounding).value

        assertEquals(
            "Meet {{JOB}} \$1 \$&, a very quirky {{HOBBY[0]}} \\d+ [a-z]*: " +
                "{{NAME}} `quoted` <b>markdown **literal**</b> opens the side quest.",
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
                        hobbies = listOf("board games? Sometimes"),
                    )

                val bio =
                    GeneratedBio.compose(
                        GeneratedBioTemplate.fromCatalog(templateId),
                        grounding,
                    ).value

                assertTrue(bio.contains(grounding.name))
                assertTrue(bio.contains(grounding.jobTitle))
                grounding.hobbies.forEach { hobby -> assertTrue(bio.contains(hobby)) }
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
                GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
                BioGrounding(
                    name = "Andrew 🧭",
                    jobTitle = "emoji\u200Dmaker",
                    hobbies = listOf("stargazing and café"),
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
            BioGrounding("Synthetic\nName", "Engineer", listOf("hiking")),
            BioGrounding("Synthetic", "Eng\u202Eineer", listOf("hiking")),
            BioGrounding("Synthetic", "Engineer", listOf("hobby\u0000")),
            BioGrounding("\uD800", "Engineer", listOf("hiking")),
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
                            PersonProfile.MAX_HOBBIES,
                        ),
                        PersonProfile.MAX_HOBBIES,
                    )
            ) {
                is BioGenerationResult.Template -> result.value
                is BioGenerationResult.Failure -> error("Fixture must be valid")
            }
        val profile =
            PersonProfile.create(
                "🧭".repeat(80),
                "J".repeat(80),
                List(PersonProfile.MAX_HOBBIES) { index ->
                    index.toString() + "H".repeat(PersonProfile.MAX_HOBBY_CODE_POINTS - 1)
                },
            )
        val exact = GeneratedBio.compose(template, profile).value

        assertEquals(BioPolicy.FINAL_BIO_MAX_CODE_POINTS, exact.codePointCount(0, exact.length))
        assertTrue(exact.contains(profile.name))
        assertTrue(exact.contains(profile.jobTitle))
        profile.hobbies.forEach { hobby -> assertTrue(exact.contains(hobby)) }
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBio.compose(
                template,
                BioGrounding(
                    profile.name,
                    profile.jobTitle,
                    List(PersonProfile.MAX_HOBBIES) { index ->
                        "H".repeat(PersonProfile.MAX_HOBBY_CODE_POINTS + if (index == 0) 1 else 0)
                    },
                ),
            )
        }
    }

    @Test
    fun `profile grounding includes every canonical hobby in first-input order`() {
        val profile =
            PersonProfile.create(
                "Synthetic",
                "Engineer",
                listOf("hiking", "pottery", "hiking", "chess"),
            )

        val bio =
            GeneratedBio.compose(
                GeneratedBioTemplate.fromCatalog(
                    BioTemplateId.QUIRKY_SIDE_QUEST,
                    profile.hobbies.size,
                ),
                profile,
            ).value

        profile.hobbies.forEach { hobby -> assertTrue(bio.contains(hobby)) }
        assertTrue(profile.hobbies.zipWithNext().all { (first, second) -> bio.indexOf(first) < bio.indexOf(second) })
    }

    @Test
    fun `ten indexed hobbies survive many creative arrangements`() {
        val profile =
            PersonProfile.create(
                "Synthetic Maximum",
                "Story engineer",
                List(PersonProfile.MAX_HOBBIES) { index -> "hobby-$index-${"x".repeat(20)}" },
            )

        repeat(100) { iteration ->
            val order =
                (0 until PersonProfile.MAX_HOBBIES).map { offset ->
                    (offset + iteration) % PersonProfile.MAX_HOBBIES
                }
            val candidate =
                "{{NAME}} is a {{JOB}}: " +
                    order.joinToString("; ") { index ->
                        "{{HOBBY[$index]}} adds quirky beat ${index + 1}"
                    } +
                    "."
            val template =
                when (val result = GeneratedBioTemplate.validate(candidate, profile.hobbies.size)) {
                    is BioGenerationResult.Template -> result.value
                    is BioGenerationResult.Failure -> error("Fixture must be valid: ${result.reason}")
                }
            val bio = GeneratedBio.compose(template, profile).value

            profile.hobbies.forEach { hobby ->
                assertEquals(1, bio.windowed(hobby.length).count { it == hobby })
            }
            assertTrue(
                order.zipWithNext().all { (first, second) ->
                    bio.indexOf(profile.hobbies[first]) < bio.indexOf(profile.hobbies[second])
                },
            )
        }
    }

    @Test
    fun `indexed template count fails closed outside the person hobby bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBioTemplate.validate("{{NAME}} is a {{JOB}}.", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedBioTemplate.validate(
                VALID_TEMPLATE,
                PersonProfile.MAX_HOBBIES + 1,
            )
        }
    }

    private fun assertInvalidTemplate(
        candidate: String,
        hobbyCount: Int = 1,
    ) {
        val result = GeneratedBioTemplate.validate(candidate, hobbyCount)
        assertTrue(result is BioGenerationResult.Failure, candidate)
        val reason = (result as BioGenerationResult.Failure).reason
        assertTrue(
            reason == BioGenerationFailure.INVALID_OUTPUT ||
                reason == BioGenerationFailure.POLICY_REJECTED,
            "$candidate -> $reason",
        )
    }

    private fun templateWithLiteralCodePoints(
        literalCodePoints: Int,
        hobbyCount: Int = 1,
    ): String {
        require(literalCodePoints > 0)
        return requiredTemplateTokens(hobbyCount).joinToString("") { it.literal } +
            "x".repeat(literalCodePoints - 1) +
            "."
    }

    private companion object {
        const val VALID_TEMPLATE =
            "Meet {{NAME}}, a very quirky {{JOB}} who enjoys {{HOBBY[0]}}."
    }
}
