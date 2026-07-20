package com.persons.finder.person.bio.remote.eval

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

        assertEquals("bio-cases-v1", corpus.id)
        assertEquals(1, corpus.schemaVersion)
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
        assertTrue(corpus.cases.all { testCase -> testCase.toRequest().macroRegion == null })
    }

    @Test
    fun `corpus parser rejects raw or unapproved fields`() {
        val error =
            assertThrows<IllegalArgumentException> {
                BioEvalCorpusLoader.parse(
                    """
                    {
                      "corpus_id": "bio-cases-v1",
                      "schema_version": 1,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage"],
                        "job_category": "other",
                        "interests": ["other"],
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
                      "corpus_id": "bio-cases-v1",
                      "schema_version": 1,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage", "single-interest", "both-other"],
                        "job_category": "other",
                        "interests": ["other"]
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
                      "corpus_id": "bio-cases-v1",
                      "schema_version": 1,
                      "cases": [{
                        "id": "case-001",
                        "slices": ["job-coverage"],
                        "job_category": "freelance-wizard",
                        "interests": ["other"]
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
                      "corpus_id": "bio-cases-v1",
                      "corpus_id": "bio-cases-v1",
                      "schema_version": 1,
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
            )
        }
    }
}
