package com.persons.finder.person.bio.remote.eval

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CancellationException

class LiveBioEvalProtocolTest {
    @Test
    fun `approved OpenAI reliability protocol is exact`() {
        assertDoesNotThrow {
            requireApprovedOpenAiReliabilityProtocol(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                caseCount = 12,
                repetitions = 25,
                maxCalls = 300,
                minimumCallInterval = Duration.ZERO,
                maximumFailureUpperBound = 0.01,
                maxOutputTokens = 256,
                modelAuthoredCodePointLimit = 512,
                finalGroundedCodePointLimit = 1_272,
                generationDeadline = Duration.ofSeconds(15),
            )
        }

        listOf(
            approved().copy(provider = LiveBioEvalProvider.GEMINI),
            approved().copy(exactModelId = "gpt-other"),
            approved().copy(caseCount = 11),
            approved().copy(repetitions = 24),
            approved().copy(maxCalls = 301),
            approved().copy(minimumCallInterval = Duration.ofMillis(1)),
            approved().copy(maximumFailureUpperBound = null),
            approved().copy(maximumFailureUpperBound = 0.02),
            approved().copy(maxOutputTokens = 255),
            approved().copy(modelAuthoredCodePointLimit = 511),
            approved().copy(finalGroundedCodePointLimit = 1_293),
            approved().copy(generationDeadline = Duration.ofSeconds(14)),
        ).forEach { invalid ->
            assertThrows<IllegalArgumentException> {
                requireApprovedOpenAiReliabilityProtocol(
                    provider = invalid.provider,
                    exactModelId = invalid.exactModelId,
                    caseCount = invalid.caseCount,
                    repetitions = invalid.repetitions,
                    maxCalls = invalid.maxCalls,
                    minimumCallInterval = invalid.minimumCallInterval,
                    maximumFailureUpperBound = invalid.maximumFailureUpperBound,
                    maxOutputTokens = invalid.maxOutputTokens,
                    modelAuthoredCodePointLimit = invalid.modelAuthoredCodePointLimit,
                    finalGroundedCodePointLimit = invalid.finalGroundedCodePointLimit,
                    generationDeadline = invalid.generationDeadline,
                )
            }
        }
    }

    @Test
    fun `executable boundary removes cancellation content and throwable links`() {
        val original =
            CancellationException("RAW-SECRET-CANCELLATION-CONTENT").apply {
                addSuppressed(IllegalStateException("RAW-SECRET-CHECKPOINT-CONTENT"))
            }

        val sanitized =
            assertThrows<CancellationException> {
                runWithSanitizedLiveBioEvalCancellation<Unit> {
                    throw original
                }
            }

        assertFalse(sanitized === original)
        assertEquals(
            "Live AI evaluation cancelled; inspect the sanitized evidence checkpoints",
            sanitized.message,
        )
        assertNull(sanitized.cause)
        assertTrue(sanitized.suppressed.isEmpty())
        assertFalse(sanitized.toString().contains("RAW-SECRET"))
    }

    private fun approved(): ProtocolValues =
        ProtocolValues(
            provider = LiveBioEvalProvider.OPENAI,
            exactModelId = "gpt-5.6-luna",
            caseCount = 12,
            repetitions = 25,
            maxCalls = 300,
            minimumCallInterval = Duration.ZERO,
            maximumFailureUpperBound = 0.01,
            maxOutputTokens = 256,
            modelAuthoredCodePointLimit = 512,
            finalGroundedCodePointLimit = 1_272,
            generationDeadline = Duration.ofSeconds(15),
        )

    private data class ProtocolValues(
        val provider: LiveBioEvalProvider,
        val exactModelId: String,
        val caseCount: Int,
        val repetitions: Int,
        val maxCalls: Int,
        val minimumCallInterval: Duration,
        val maximumFailureUpperBound: Double?,
        val maxOutputTokens: Int,
        val modelAuthoredCodePointLimit: Int,
        val finalGroundedCodePointLimit: Int,
        val generationDeadline: Duration,
    )
}
