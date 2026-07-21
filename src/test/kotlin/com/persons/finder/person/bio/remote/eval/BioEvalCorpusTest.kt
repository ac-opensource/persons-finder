package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BioEvalCorpusTest {
    @Test
    fun `versioned corpus covers every safe code and required shape`() {
        val corpus = BioEvalCorpusLoader.load()

        assertEquals("bio-cases-v2", corpus.id)
        assertEquals(2, corpus.schemaVersion)
        assertTrue(Regex("[a-f0-9]{64}").matches(corpus.sha256))
        assertEquals(
            SafeJobCode.entries.toSet(),
            corpus.cases.mapTo(mutableSetOf(), BioEvalCase::jobCategory),
        )
        assertEquals(
            SafeInterestCode.entries.toSet(),
            corpus.cases.flatMapTo(mutableSetOf(), BioEvalCase::interests),
        )
        assertTrue(
            corpus.cases.any { testCase ->
                testCase.jobCategory == SafeJobCode.OTHER &&
                    testCase.interests == listOf(SafeInterestCode.OTHER)
            },
        )
        assertTrue(corpus.cases.any { testCase -> testCase.interests.size > 1 })
        assertEquals(
            mapOf("case-004" to 3, "case-012" to 10),
            corpus.cases
                .filter { testCase -> testCase.hobbyCount > 1 }
                .associate { testCase -> testCase.id to testCase.hobbyCount },
        )
        assertTrue(
            corpus.cases.all { testCase ->
                testCase.toRequest().macroRegion == null &&
                    testCase.toRequest().hobbyCount == testCase.hobbyCount
            },
        )
        assertTrue(
            corpus.cases.single { testCase ->
                testCase.hobbyCount == BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS
            }.slices.contains("maximum-hobbies"),
        )
    }

    @Test
    fun `corpus parser rejects raw or unapproved fields`() {
        val error =
            assertThrows<IllegalArgumentException> {
                BioEvalCorpusLoader.parse(
                    """
                    {
                      "corpus_id": "bio-cases-v2",
                      "schema_version": 2,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage", "single-interest", "single-hobby"],
                        "job_category": "other",
                        "interests": ["other"],
                        "hobby_count": 1,
                        "name": "raw-source-value"
                      }]
                    }
                    """.trimIndent().toByteArray(),
                )
            }

        assertTrue(error.message.orEmpty().contains("approved typed fields"))
    }

    @Test
    fun `corpus parser rejects incomplete safe-code coverage`() {
        val error =
            assertThrows<IllegalArgumentException> {
                BioEvalCorpusLoader.parse(
                    """
                    {
                      "corpus_id": "bio-cases-v2",
                      "schema_version": 2,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage", "single-interest", "single-hobby", "both-other"],
                        "job_category": "other",
                        "interests": ["other"],
                        "hobby_count": 1
                      }]
                    }
                    """.trimIndent().toByteArray(),
                )
            }

        assertTrue(error.message.orEmpty().contains("every safe job code"))
    }

    @Test
    fun `corpus parser rejects unknown safe-code wire values`() {
        val error =
            assertThrows<IllegalArgumentException> {
                BioEvalCorpusLoader.parse(
                    """
                    {
                      "corpus_id": "bio-cases-v2",
                      "schema_version": 2,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage", "single-interest", "single-hobby"],
                        "job_category": "freelance-wizard",
                        "interests": ["other"],
                        "hobby_count": 1
                      }]
                    }
                    """.trimIndent().toByteArray(),
                )
            }

        assertTrue(error.message.orEmpty().contains("safe job code"))
    }

    @Test
    fun `corpus parser rejects duplicate JSON fields before tree construction`() {
        val error =
            assertThrows<IllegalArgumentException> {
                BioEvalCorpusLoader.parse(
                    """
                    {
                      "corpus_id": "bio-cases-v2",
                      "corpus_id": "bio-cases-v2",
                      "schema_version": 2,
                      "cases": []
                    }
                    """.trimIndent().toByteArray(),
                )
            }

        assertTrue(error.message.orEmpty().contains("not valid JSON"))
    }

    @Test
    fun `typed case rejects slices that do not match its structure`() {
        assertThrows<IllegalArgumentException> {
            BioEvalCase(
                id = "case-001",
                slices = setOf("job-coverage", "single-interest"),
                jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
                interests =
                    listOf(
                        SafeInterestCode.MUSIC,
                        SafeInterestCode.TRAVEL,
                    ),
                hobbyCount = 2,
            )
        }
    }

    @Test
    fun `corpus parser rejects missing malformed and out-of-range hobby counts`() {
        listOf(null, "1.5", "0", "11").forEach { value ->
            val hobbyCountField = value?.let { ", \"hobby_count\": $it" }.orEmpty()
            val error =
                assertThrows<IllegalArgumentException> {
                    BioEvalCorpusLoader.parse(
                        """
                        {
                          "corpus_id": "bio-cases-v2",
                          "schema_version": 2,
                          "cases": [{
                            "id": "case-001",
                            "slices": ["job-coverage", "single-interest", "single-hobby"],
                            "job_category": "other",
                            "interests": ["other"]$hobbyCountField
                          }]
                        }
                        """.trimIndent().toByteArray(),
                    )
                }

            assertTrue(
                error.message.orEmpty().contains("approved typed fields") ||
                    error.message.orEmpty().contains("hobby count") ||
                    error.message.orEmpty().contains("must be an integer"),
                error.message,
            )
        }
    }
}
