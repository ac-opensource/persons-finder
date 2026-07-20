package com.persons.finder.person.bio

import com.persons.finder.person.bio.remote.MAX_REMOTE_GENERATOR_OUTPUT_CHARS
import com.persons.finder.person.bio.remote.MAX_REMOTE_PROVIDER_OUTPUT_TOKENS
import com.persons.finder.person.bio.remote.MAX_PROVIDER_RESPONSE_BYTES
import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import com.persons.finder.person.create.CreatePersonResponse
import com.persons.finder.person.nearby.NearbyPersonResponse
import io.swagger.v3.oas.annotations.media.Schema
import com.persons.finder.person.model.PersonProfile
import com.persons.finder.person.model.ProfileValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class BioBoundsContractTest {
    @Test
    fun `public create and nearby schemas share the final bio limit`() {
        listOf(
            CreatePersonResponse::class.java,
            NearbyPersonResponse::class.java,
        ).forEach { responseType ->
            val schema =
                requireNotNull(
                    responseType.getDeclaredField("bio").getAnnotation(Schema::class.java),
                )
            assertEquals(BioPolicy.FINAL_BIO_MAX_CODE_POINTS, schema.maxLength)
        }
    }

    @Test
    fun `provider envelope cap leaves bounded headroom above the temporary token ceiling`() {
        assertTrue(
            MAX_PROVIDER_RESPONSE_BYTES >= MAX_REMOTE_PROVIDER_OUTPUT_TOKENS * 16,
        )
        assertTrue(MAX_PROVIDER_RESPONSE_BYTES > MAX_REMOTE_GENERATOR_OUTPUT_CHARS)
    }

    @Test
    fun `job title bound accepts below and exact then rejects above`() {
        listOf(
            PersonProfile.MAX_JOB_TITLE_CODE_POINTS - 1,
            PersonProfile.MAX_JOB_TITLE_CODE_POINTS,
        ).forEach { size ->
            assertEquals(
                size,
                PersonProfile.create("N", "J".repeat(size), listOf("H"))
                    .jobTitle.codePointCount(0, size),
            )
        }
        assertThrows(ProfileValidationException::class.java) {
            PersonProfile.create(
                "N",
                "J".repeat(PersonProfile.MAX_JOB_TITLE_CODE_POINTS + 1),
                listOf("H"),
            )
        }
    }

    @Test
    fun `hobby item and item-count bounds accept below and exact then reject above`() {
        listOf(
            PersonProfile.MAX_HOBBY_CODE_POINTS - 1,
            PersonProfile.MAX_HOBBY_CODE_POINTS,
        ).forEach { size ->
            assertEquals(
                size,
                PersonProfile.create("N", "J", listOf("H".repeat(size)))
                    .hobbies.single().length,
            )
        }
        assertThrows(ProfileValidationException::class.java) {
            PersonProfile.create(
                "N",
                "J",
                listOf("H".repeat(PersonProfile.MAX_HOBBY_CODE_POINTS + 1)),
            )
        }

        listOf(PersonProfile.MAX_HOBBIES - 1, PersonProfile.MAX_HOBBIES).forEach { count ->
            assertEquals(
                count,
                PersonProfile.create("N", "J", List(count) { "hobby-$it" }).hobbies.size,
            )
        }
        assertThrows(ProfileValidationException::class.java) {
            PersonProfile.create(
                "N",
                "J",
                List(PersonProfile.MAX_HOBBIES + 1) { "hobby-$it" },
            )
        }
    }

    @Test
    fun `aggregate source ceiling follows the independently bounded field maxima`() {
        val exact =
            PersonProfile.create(
                "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS),
                "J".repeat(PersonProfile.MAX_JOB_TITLE_CODE_POINTS),
                List(PersonProfile.MAX_HOBBIES) { index ->
                    index.toString() + "H".repeat(PersonProfile.MAX_HOBBY_CODE_POINTS - 1)
                },
            )
        val aggregate =
            exact.name.codePointCount(0, exact.name.length) +
                exact.jobTitle.codePointCount(0, exact.jobTitle.length) +
                exact.hobbies.sumOf { it.codePointCount(0, it.length) }

        assertEquals(PersonProfile.MAX_AGGREGATE_SOURCE_CODE_POINTS, aggregate)
        assertThrows(ProfileValidationException::class.java) {
            PersonProfile.create(
                "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS),
                "J".repeat(PersonProfile.MAX_JOB_TITLE_CODE_POINTS + 1),
                exact.hobbies,
            )
        }
    }

    @Test
    fun `NFC normalization is applied before code-point bounds`() {
        val decomposed = "e\u0301".repeat(PersonProfile.MAX_NAME_CODE_POINTS)
        val profile = PersonProfile.create(decomposed, "J", listOf("H"))

        assertEquals(PersonProfile.MAX_NAME_CODE_POINTS, profile.name.codePointCount(0, profile.name.length))
        assertThrows(ProfileValidationException::class.java) {
            PersonProfile.create(decomposed + "x", "J", listOf("H"))
        }
    }

    @Test
    fun `remote structured output cap covers below exact and above`() {
        fun resultForSize(size: Int): BioGenerationResult =
            RemoteBioGenerator(
                providerClient =
                    ModelProviderClient {
                        ModelProviderResult.Generated(" ".repeat(size))
                    },
                objectMapper = tools.jackson.databind.json.JsonMapper.builder().build(),
            ).generate(
                BioTemplateRequest(
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                ),
            )

        listOf(
            MAX_REMOTE_GENERATOR_OUTPUT_CHARS - 1,
            MAX_REMOTE_GENERATOR_OUTPUT_CHARS,
            MAX_REMOTE_GENERATOR_OUTPUT_CHARS + 1,
        ).forEach { size ->
            assertEquals(
                BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                resultForSize(size),
            )
        }
    }

    @Test
    fun `application deadline is monotonic and expires without adapter work`() {
        var now = 10L
        val context =
            BioGenerationContext.start(Duration.ofNanos(2)) {
                now
            }
        assertEquals(Duration.ofNanos(2), context.remaining())
        now = 11L
        assertEquals(Duration.ofNanos(1), context.remaining())
        now = 12L
        assertEquals(Duration.ZERO, context.remaining())

        var calls = 0
        val generator =
            BioGenerator {
                calls++
                BioGenerationResult.Template(
                    GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
                )
            }
        assertEquals(
            BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT),
            generator.generate(
                BioTemplateRequest(
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                ),
                context,
            ),
        )
        assertEquals(0, calls)
        assertTrue(context.remaining().isZero)
    }
}
