package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.GeneratedBioTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import tools.jackson.databind.json.JsonMapper

class LiveBioEvalMarkdownReportTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `renderer produces a human-readable recomputable ledger without content`() {
        val rendered = LiveBioEvalMarkdownReport.render(sanitizedReport())

        assertTrue(rendered.startsWith("# OpenAI live bio evaluation evidence"))
        assertTrue(rendered.contains("> Result: **PASS**"))
        assertTrue(rendered.contains("## Results by round"))
        assertTrue(rendered.contains("## Per-case results"))
        assertTrue(rendered.contains("## Per-slice results"))
        assertTrue(rendered.contains("Show all 12 content-free attempt records"))
        assertTrue(rendered.contains("| 12 | 200 | completed |"))
        assertTrue(rendered.contains("| 12 | 1 | 12 | case-012 |"))
        assertTrue(rendered.length < 100_000)
        assertTrue(rendered.contains("case-012"))
        assertTrue(rendered.contains("valid_prose"))
        assertTrue(rendered.contains("0.183981195"))
        assertTrue(rendered.contains("Estimated usage (USD)"))
        assertTrue(rendered.contains("Application generation deadline (ms)"))
        assertTrue(rendered.contains("15000"))
        assertTrue(rendered.contains("https://developers.openai.com/api/docs/pricing"))
        assertTrue(rendered.contains("actual billing can differ"))
        assertTrue(rendered.contains("LIVE_AI_EVAL_REPETITIONS=1"))
        assertFalse(rendered.contains("RAW-MODEL-PROSE"))
        assertFalse(rendered.contains("RAW-API-KEY"))
        assertFalse(rendered.contains("{{NAME}}"))
        assertFalse(rendered.contains("technology_engineering"))
    }

    @Test
    fun `passing report rejects missing or orphan joined evidence`() {
        val missingRequest = sanitizedReport().toMutableMap()
        val execution = missingRequest.requiredMutableMap("execution")
        val requestEvidence = execution.requiredMutableMap("provider_request_evidence")
        requestEvidence["requests"] =
            (requestEvidence.getValue("requests") as List<*>).dropLast(1)

        assertThrows(IllegalArgumentException::class.java) {
            LiveBioEvalMarkdownReport.render(missingRequest)
        }

        val orphanResponse = sanitizedReport().toMutableMap()
        val orphanExecution = orphanResponse.requiredMutableMap("execution")
        val responseEvidence = orphanExecution.requiredMutableMap("provider_response_evidence")
        @Suppress("UNCHECKED_CAST")
        val providerAttempts =
            (responseEvidence.getValue("attempts") as List<Map<String, Any?>>).toMutableList()
        providerAttempts +=
            linkedMapOf(
                "attempt_index" to 13,
                "kind" to "response",
            )
        responseEvidence["attempts"] = providerAttempts

        assertThrows(IllegalArgumentException::class.java) {
            LiveBioEvalMarkdownReport.render(orphanResponse)
        }
    }

    @Test
    fun `failed checkpoint remains readable when transport records are absent`() {
        val report = sanitizedReport(passed = false).toMutableMap()
        val execution = report.requiredMutableMap("execution")
        execution.requiredMutableMap("provider_request_evidence")["requests"] = emptyList<Any>()
        execution.requiredMutableMap("provider_response_evidence")["attempts"] = emptyList<Any>()
        execution.requiredMutableMap("application_generation_diagnostics")["events"] = emptyList<Any>()

        val rendered = LiveBioEvalMarkdownReport.render(report)

        assertTrue(rendered.contains("> Result: **FAIL / INCOMPLETE**"))
        assertTrue(rendered.contains("—"))
    }

    @Test
    fun `writer preserves ignored JSON and matching Markdown checkpoints`() {
        val report = sanitizedReport()
        val paths =
            writeLiveBioEvalEvidenceCopies(
                objectMapper = JsonMapper.builder().build(),
                report = report,
                scratchJsonPath = temporaryDirectory.resolve("scratch/report.json"),
                durableReportDirectory = temporaryDirectory.resolve("durable"),
                codeRevision = "a".repeat(40),
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                startedAt = Instant.parse("2026-07-20T00:00:00Z"),
            )

        assertTrue(Files.exists(paths.json.scratchPath))
        assertTrue(Files.exists(paths.json.durablePath))
        assertTrue(Files.exists(paths.markdown.scratchPath))
        assertTrue(Files.exists(paths.markdown.durablePath))
        assertEquals("report.md", paths.markdown.scratchPath.fileName.toString())
        assertTrue(paths.markdown.durablePath.fileName.toString().endsWith("-report.md"))
        assertTrue(Files.readString(paths.markdown.scratchPath).contains("> Result: **PASS**"))
        assertTrue(Files.readString(paths.json.scratchPath).contains("\"report_schema_version\" : 6"))
    }

    private fun sanitizedReport(passed: Boolean = true): Map<String, Any> {
        var ticker = 0L
        val typedReport =
            LiveBioEvalRunner(
                clock =
                    Clock.fixed(
                        Instant.parse("2026-07-20T00:00:00Z"),
                        ZoneOffset.UTC,
                    ),
                nanoTime = {
                    val current = ticker
                    ticker += 1_000_000
                    current
                },
            ).run(
                corpus = BioEvalCorpusLoader.load(),
                configuration =
                    LiveBioEvalConfiguration(
                        provider = "openai",
                        exactModelId = "gpt-5.6-luna",
                        codeRevision = "a".repeat(40),
                        promptSha256 = "b".repeat(64),
                        outputSchemaSha256 = "c".repeat(64),
                        maxOutputTokens = 256,
                        repetitions = 1,
                        maxCalls = 12,
                        minimumCallInterval = Duration.ZERO,
                    ),
                generator =
                    BioGenerator { request ->
                        BioGenerationResult.Template(validTemplate(request.hobbyCount))
                    },
            )
        val requests =
            (1..12).map { index ->
                linkedMapOf<String, Any?>(
                    "request_index" to index,
                    "request_body_bytes" to 1_850 + index,
                    "timeout_millis" to 9_999,
                    "expected_configuration_matched" to true,
                )
            }
        val providerAttempts =
            (1..12).map { index ->
                linkedMapOf<String, Any?>(
                    "attempt_index" to index,
                    "kind" to "response",
                    "http_status_code" to 200,
                    "provider_status" to "completed",
                    "diagnostic" to "completed_structured_output",
                    "response_body_bytes" to 3_800 + index,
                    "transport_latency_millis" to 1_000 + index,
                    "input_tokens" to 250,
                    "output_tokens" to 50,
                    "total_tokens" to 300,
                    "cached_input_tokens" to 0,
                    "reasoning_or_thinking_tokens" to 0,
                    "tool_use_prompt_tokens" to null,
                    "provider_output_text_code_points" to 170,
                    "refusal_item_count" to 0,
                    "safety_rating_count" to 0,
                    "safe_metadata_headers" to
                        mapOf("openai-processing-ms" to "900"),
                )
            }
        val applicationEvents =
            (1..12).map { index ->
                linkedMapOf<String, Any?>(
                    "invocation_index" to index,
                    "diagnostic" to "valid",
                    "name_placeholder_count" to 1,
                    "job_placeholder_count" to 1,
                    "hobby_placeholder_count" to 1,
                    "printable_ascii" to true,
                )
            }
        return typedReport.toSanitizedMap().toMutableMap().apply {
            put(
                "execution",
                linkedMapOf(
                    "execution_finalized" to passed,
                    "model_provider_client_invocations" to 12,
                    "attempted_calls" to 12,
                    "not_attempted_calls" to 0,
                    "retries" to 0,
                    "top_up_calls" to 0,
                    "provider_fallbacks" to 0,
                    "stop_reason" to if (passed) "completed" else "early_stop",
                    "delegated_http_send_attempts" to 12,
                    "working_tree_clean" to true,
                    "hard_boundary_violation_count" to 0,
                    "hard_boundary_violation_counts" to emptyMap<String, Int>(),
                    "provider_request_evidence" to
                        linkedMapOf(
                            "request_count" to 12,
                            "all_requests_match_expected_configuration" to true,
                            "requests" to requests,
                        ),
                    "provider_response_evidence" to
                        linkedMapOf(
                            "attempt_count" to 12,
                            "response_count" to 12,
                            "usage_reported_response_count" to 12,
                            "input_tokens" to 3_000,
                            "output_tokens" to 600,
                            "total_tokens" to 3_600,
                            "maximum_output_tokens_per_response" to 50,
                            "provider_request_id_sequence_sha256" to "d".repeat(64),
                            "provider_response_id_sequence_sha256" to "e".repeat(64),
                            "attempts" to providerAttempts,
                        ),
                    "application_generation_diagnostics" to
                        linkedMapOf(
                            "diagnostic_count" to 12,
                            "diagnostic_counts" to mapOf("valid" to 12),
                            "maximum_model_authored_code_points" to 44,
                            "maximum_sentence_count" to 1,
                            "events" to applicationEvents,
                        ),
                ),
            )
            put(
                "billing",
                linkedMapOf(
                    "provider_usage_when_present_is_metering_evidence" to true,
                    "provider_response_received" to true,
                    "provider_usage_reported" to true,
                    "usage_reported_response_count" to 12,
                    "metered_processing_evidenced" to true,
                    "actual_billed_usd" to null,
                    "actual_billing_requires_provider_billing_export" to true,
                ),
            )
            put(
                "gate",
                linkedMapOf(
                    "execution_finalized" to passed,
                    "reliability_gate_configured" to true,
                    "maximum_one_sided_95_percent_wilson_upper_failure_bound" to 0.01,
                    "synthetic_retention_and_data_use_approved" to true,
                    "hard_boundary_violations" to 0,
                    "harness_errors" to 0,
                    "grounded_evidence_complete" to true,
                    "evidence_complete" to true,
                    "all_planned_calls_completed" to true,
                    "passed" to passed,
                ),
            )
        }
    }

    private fun validTemplate(hobbyCount: Int): GeneratedBioTemplate =
        when (
            val result =
                GeneratedBioTemplate.validate(
                    "{{NAME}} makes " +
                        (0 until hobbyCount).joinToString("; ") { index ->
                            "{{HOBBY[$index]}}"
                        } +
                        " delightfully quirky as a {{JOB}}.",
                    hobbyCount,
                )
        ) {
            is BioGenerationResult.Template -> result.value
            is BioGenerationResult.Failure -> error("Fixture must be valid")
        }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.requiredMutableMap(
        key: String,
    ): MutableMap<String, Any?> =
        getValue(key) as MutableMap<String, Any?>
}
