package com.persons.finder.person.bio

import com.persons.finder.person.create.CreatePersonCommand
import com.persons.finder.person.create.CreatePersonOutcome
import com.persons.finder.person.create.CreatePersonRepository
import com.persons.finder.person.create.CreatePersonResult
import com.persons.finder.person.create.CreatePersonService
import com.persons.finder.person.create.NewPerson
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.PersonProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlin.reflect.full.memberProperties

class BioPrivacyBoundaryTest {
    private val policy = BioPolicy()

    @Test
    fun `canonical generator request has the exact typed allowlist and no raw source`() {
        val profile =
            PersonProfile.create(
                name = "Synthetic Person 73",
                jobTitle = "Robotics engineer at Acme for Alice",
                hobbies =
                    listOf(
                        "hiking",
                        "123 Queen St",
                        "-43.5,172.6",
                        "Private Example Club member",
                    ),
            )
        val prepared = policy.prepare(profile)
        val request = prepared.request

        assertEquals(
            setOf(
                "displayName",
                "locale",
                "countryCode",
                "jobCategory",
                "jobCategoryMappingVersion",
                "interests",
                "interestCategoryMappingVersion",
                "macroRegion",
                "tone",
            ),
            BioTemplateRequest::class.memberProperties.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("request", "selectedHobby"),
            PreparedBioRequest::class.memberProperties.map { it.name }.toSet(),
        )
        assertEquals("{{NAME}}", request.displayName)
        assertEquals("en-NZ", request.locale)
        assertEquals("NZ", request.countryCode)
        assertEquals(SafeJobCode.OTHER, request.jobCategory)
        assertEquals(
            listOf(SafeInterestCode.OUTDOORS_NATURE, SafeInterestCode.OTHER),
            request.interests,
        )
        assertEquals(null, request.macroRegion)
        assertEquals(BioTone.QUIRKY, request.tone)
        assertEquals("hiking", prepared.selectedHobby)

        val representable = request.toString()
        listOf(
            profile.name,
            profile.jobTitle,
            "Acme",
            "Alice",
            "123 Queen St",
            "-43.5,172.6",
            "Private Example Club",
            "person-id",
            "oidc-subject",
            "access-token",
        ).forEach { forbidden ->
            assertFalse(representable.contains(forbidden), forbidden)
        }
    }

    @Test
    fun `closed types guard adapter success final bio and persistence boundaries`() {
        assertEquals(
            GeneratedBioTemplate::class,
            BioGenerationResult.Template::class.memberProperties
                .single { it.name == "value" }
                .returnType.classifier,
        )
        assertEquals(
            GeneratedBio::class,
            NewPerson::class.memberProperties.single { it.name == "bio" }.returnType.classifier,
        )
        assertEquals(
            GeneratedBio::class,
            CreatePersonResult::class.memberProperties.single { it.name == "bio" }.returnType.classifier,
        )
    }

    @Test
    fun `request invariants fail closed and copy mutable interests`() {
        listOf(
            { BioTemplateRequest(jobCategory = SafeJobCode.OTHER, interests = emptyList()) },
            {
                BioTemplateRequest(
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER, SafeInterestCode.OTHER),
                )
            },
            {
                BioTemplateRequest(
                    displayName = "Synthetic Person",
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                )
            },
            {
                BioTemplateRequest(
                    locale = "en-US",
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                )
            },
            {
                BioTemplateRequest(
                    countryCode = "US",
                    jobCategory = SafeJobCode.OTHER,
                    interests = listOf(SafeInterestCode.OTHER),
                )
            },
            {
                BioTemplateRequest(
                    jobCategory = SafeJobCode.OTHER,
                    jobCategoryMappingVersion = "job-v2",
                    interests = listOf(SafeInterestCode.OTHER),
                )
            },
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid() }
        }

        val mutable = mutableListOf(SafeInterestCode.OTHER)
        val request = BioTemplateRequest(jobCategory = SafeJobCode.OTHER, interests = mutable)
        mutable.clear()
        assertEquals(listOf(SafeInterestCode.OTHER), request.interests)
        assertThrows(IllegalArgumentException::class.java) {
            MacroRegion.valueOf("SOUTH_ISLAND_EXACT")
        }
    }

    @Test
    fun `wire catalogs are the exhaustive approved closed sets`() {
        assertEquals(
            listOf(
                "technology_engineering",
                "healthcare",
                "education_research",
                "creative_media",
                "business_operations",
                "finance_legal",
                "sales_service",
                "trades_manufacturing",
                "hospitality_retail",
                "public_community_service",
                "student",
                "other",
            ),
            SafeJobCode.entries.map(SafeJobCode::wireValue),
        )
        assertEquals(
            listOf(
                "outdoors_nature",
                "sports_fitness",
                "arts_crafts",
                "music",
                "reading_writing",
                "food_drink",
                "games_puzzles",
                "technology_making",
                "gardening",
                "travel",
                "other",
            ),
            SafeInterestCode.entries.map(SafeInterestCode::wireValue),
        )
        assertEquals(
            listOf("North Island", "South Island"),
            MacroRegion.entries.map(MacroRegion::wireValue),
        )
    }

    @Test
    fun `production alias catalogs exactly match the reviewed mappings`() {
        assertEquals(JOB_ALIASES.toMap(), ReviewedBioAliases.jobs)
        assertEquals(INTEREST_ALIASES.toMap(), ReviewedBioAliases.interests)
    }

    @TestFactory
    fun `every reviewed job alias maps exactly with no prefix substring or fuzzy match`(): List<DynamicTest> =
        JOB_ALIASES.map { (alias, expected) ->
            DynamicTest.dynamicTest(alias) {
                assertEquals(
                    expected,
                    policy.prepare(PersonProfile.create("Synthetic", alias, listOf("hiking")))
                        .request.jobCategory,
                )
                listOf("Senior $alias", "$alias at Acme", "weekend $alias").forEach { nonExact ->
                    assertEquals(
                        SafeJobCode.OTHER,
                        policy.prepare(PersonProfile.create("Synthetic", nonExact, listOf("hiking")))
                            .request.jobCategory,
                    )
                }
            }
        }

    @TestFactory
    fun `every reviewed hobby alias maps exactly and is eligible for grounding`(): List<DynamicTest> =
        INTEREST_ALIASES.map { (alias, expected) ->
            DynamicTest.dynamicTest(alias) {
                val exact =
                    policy.prepare(
                        PersonProfile.create("Synthetic", "Unmapped role", listOf("unknown", alias)),
                    )
                assertEquals(listOf(SafeInterestCode.OTHER, expected), exact.request.interests)
                assertEquals(alias, exact.selectedHobby)
                assertEquals(
                    listOf(SafeInterestCode.OTHER),
                    policy.prepare(
                        PersonProfile.create(
                            "Synthetic",
                            "Unmapped role",
                            listOf("weekend $alias club"),
                        ),
                    ).request.interests,
                )
            }
        }

    @Test
    fun `unsafe inputs preserve zero generator calls and zero writes`() {
        BioPolicyTest.NAMED_ATTACKS.forEach { (_, job, hobbies) ->
            val repository = RecordingRepository()
            val generator = CapturingGenerator()
            val outcome =
                createService(repository, generator).execute(
                    createCommand(PersonProfile.create("Synthetic Person", job, hobbies)),
                )

            assertEquals(CreatePersonOutcome.UnsafeBioInput, outcome)
            assertEquals(0, generator.invocations)
            repository.assertNoWrites()
        }
    }

    @Test
    fun `central failure mapping preserves reasons while exposing invalid versus unavailable outcomes`() {
        BioGenerationFailure.entries.forEach { failure ->
            val repository = RecordingRepository()
            val outcome =
                createService(repository, BioGenerator { BioGenerationResult.Failure(failure) })
                    .execute(
                        createCommand(
                            PersonProfile.create("Synthetic", "Software engineer", listOf("hiking")),
                        ),
                    )

            when (failure) {
                BioGenerationFailure.INVALID_OUTPUT,
                BioGenerationFailure.POLICY_REJECTED,
                -> assertEquals(CreatePersonOutcome.BioGenerationInvalid(failure), outcome)

                BioGenerationFailure.TIMEOUT,
                BioGenerationFailure.RATE_LIMITED,
                BioGenerationFailure.UNAVAILABLE,
                -> assertEquals(CreatePersonOutcome.BioGenerationUnavailable(failure), outcome)
            }
            repository.assertNoWrites()
        }
    }

    @Test
    fun `both-other request is generic remotely and grounds exact originals only locally`() {
        val repository = RecordingRepository()
        val generator = CapturingGenerator()
        val profile =
            PersonProfile.create(
                "Synthetic Person",
                "Orbital lift coordinator",
                listOf("rare clock restoration"),
            )

        val outcome = createService(repository, generator).execute(createCommand(profile))

        assertTrue(outcome is CreatePersonOutcome.Created)
        assertEquals(SafeJobCode.OTHER, generator.requests.single().jobCategory)
        assertEquals(listOf(SafeInterestCode.OTHER), generator.requests.single().interests)
        assertFalse(generator.requests.single().toString().contains(profile.jobTitle))
        assertFalse(generator.requests.single().toString().contains(profile.hobbies.single()))
        val bio = repository.people.single().bio.value
        assertTrue(bio.contains(profile.name))
        assertTrue(bio.contains(profile.jobTitle))
        assertTrue(bio.contains(profile.hobbies.single()))
    }

    @Test
    fun `generated prose beyond the literal budget is 502-class and leaves no writes`() {
        val repository = RecordingRepository()
        val profile =
            PersonProfile.create(
                "N".repeat(80),
                "J".repeat(80),
                listOf("H".repeat(46)),
            )
        val outcome =
            createService(
                repository,
                BioGenerator {
                    GeneratedBioTemplate.validate(
                        "{{NAME}}{{JOB}}{{HOBBY}}" +
                            "x".repeat(BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS) +
                            ".",
                    )
                },
            ).execute(createCommand(profile))

        assertEquals(
            CreatePersonOutcome.BioGenerationInvalid(BioGenerationFailure.INVALID_OUTPUT),
            outcome,
        )
        repository.assertNoWrites()
    }

    @Test
    fun `cancellation propagates and never becomes failure fallback or persistence`() {
        val repository = RecordingRepository()
        var deterministicFallbackCalls = 0
        val generator =
            BioGenerator {
                throw CancellationException("synthetic cancellation")
            }

        assertThrows(CancellationException::class.java) {
            createService(repository, generator).execute(
                createCommand(
                    PersonProfile.create("Synthetic", "Software engineer", listOf("hiking")),
                ),
            )
        }
        assertEquals(0, deterministicFallbackCalls)
        repository.assertNoWrites()
    }

    private fun createService(
        repository: RecordingRepository,
        generator: BioGenerator,
    ) = CreatePersonService(
        repository = repository,
        bioGenerator = generator,
        bioPolicy = policy,
        transactions = TransactionOperations.withoutTransaction(),
        clock = FIXED_CLOCK,
    )

    private fun createCommand(profile: PersonProfile) =
        CreatePersonCommand(
            profile = profile,
            initialLocation = GeoPoint.from(-41.2865, 174.7762),
        )

    private class CapturingGenerator(
        private val template: GeneratedBioTemplate =
            GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
    ) : BioGenerator {
        val requests = mutableListOf<BioTemplateRequest>()
        var invocations = 0

        override fun generate(request: BioTemplateRequest): BioGenerationResult {
            invocations++
            requests += request
            return BioGenerationResult.Template(template)
        }
    }

    private class RecordingRepository : CreatePersonRepository {
        val people = mutableListOf<NewPerson>()
        val observations = mutableListOf<LocationObservation>()
        val projections = mutableListOf<LastKnownLocationProjection>()

        override fun insertPerson(person: NewPerson) {
            people += person
        }

        override fun insertObservation(observation: LocationObservation) {
            observations += observation
        }

        override fun insertLastKnown(projection: LastKnownLocationProjection) {
            projections += projection
        }

        fun assertNoWrites() {
            assertEquals(0, people.size)
            assertEquals(0, observations.size)
            assertEquals(0, projections.size)
        }
    }

    private companion object {
        val FIXED_CLOCK: Clock =
            Clock.fixed(Instant.parse("2026-07-19T05:06:07.123456Z"), ZoneOffset.UTC)

        val JOB_ALIASES =
            listOf(
                "software engineer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
                "software developer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
                "web developer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
                "programmer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
                "doctor" to SafeJobCode.HEALTHCARE,
                "nurse" to SafeJobCode.HEALTHCARE,
                "teacher" to SafeJobCode.EDUCATION_RESEARCH,
                "lecturer" to SafeJobCode.EDUCATION_RESEARCH,
                "researcher" to SafeJobCode.EDUCATION_RESEARCH,
                "designer" to SafeJobCode.CREATIVE_MEDIA,
                "artist" to SafeJobCode.CREATIVE_MEDIA,
                "writer" to SafeJobCode.CREATIVE_MEDIA,
                "journalist" to SafeJobCode.CREATIVE_MEDIA,
                "project manager" to SafeJobCode.BUSINESS_OPERATIONS,
                "operations manager" to SafeJobCode.BUSINESS_OPERATIONS,
                "administrator" to SafeJobCode.BUSINESS_OPERATIONS,
                "accountant" to SafeJobCode.FINANCE_LEGAL,
                "lawyer" to SafeJobCode.FINANCE_LEGAL,
                "solicitor" to SafeJobCode.FINANCE_LEGAL,
                "salesperson" to SafeJobCode.SALES_SERVICE,
                "customer service representative" to SafeJobCode.SALES_SERVICE,
                "electrician" to SafeJobCode.TRADES_MANUFACTURING,
                "plumber" to SafeJobCode.TRADES_MANUFACTURING,
                "mechanic" to SafeJobCode.TRADES_MANUFACTURING,
                "carpenter" to SafeJobCode.TRADES_MANUFACTURING,
                "chef" to SafeJobCode.HOSPITALITY_RETAIL,
                "barista" to SafeJobCode.HOSPITALITY_RETAIL,
                "retail assistant" to SafeJobCode.HOSPITALITY_RETAIL,
                "firefighter" to SafeJobCode.PUBLIC_COMMUNITY_SERVICE,
                "social worker" to SafeJobCode.PUBLIC_COMMUNITY_SERVICE,
                "student" to SafeJobCode.STUDENT,
            )

        val INTEREST_ALIASES =
            listOf(
                "hiking" to SafeInterestCode.OUTDOORS_NATURE,
                "tramping" to SafeInterestCode.OUTDOORS_NATURE,
                "running" to SafeInterestCode.SPORTS_FITNESS,
                "cycling" to SafeInterestCode.SPORTS_FITNESS,
                "pottery" to SafeInterestCode.ARTS_CRAFTS,
                "painting" to SafeInterestCode.ARTS_CRAFTS,
                "guitar" to SafeInterestCode.MUSIC,
                "piano" to SafeInterestCode.MUSIC,
                "reading" to SafeInterestCode.READING_WRITING,
                "creative writing" to SafeInterestCode.READING_WRITING,
                "espresso" to SafeInterestCode.FOOD_DRINK,
                "cooking" to SafeInterestCode.FOOD_DRINK,
                "chess" to SafeInterestCode.GAMES_PUZZLES,
                "crosswords" to SafeInterestCode.GAMES_PUZZLES,
                "coding" to SafeInterestCode.TECHNOLOGY_MAKING,
                "woodworking" to SafeInterestCode.TECHNOLOGY_MAKING,
                "gardening" to SafeInterestCode.GARDENING,
                "travel" to SafeInterestCode.TRAVEL,
            )
    }
}
