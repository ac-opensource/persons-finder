package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGrounding
import com.persons.finder.person.bio.GeneratedBio
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.remote.MAX_REMOTE_PROVIDER_OUTPUT_TOKENS
import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.ProviderHttpRequest
import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import com.persons.finder.person.bio.remote.RemoteBioGenerationDiagnostic
import com.persons.finder.person.bio.remote.RemoteBioGenerationDiagnosticEvent
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import tools.jackson.databind.json.JsonMapper

class LiveBioSmokeReportTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `evidence destinations are proven writable before a provider call`() {
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                codeRevision = "a".repeat(40),
                fixtureId = "live-bio-smoke-v2",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = "c".repeat(64),
                outputSchemaSha256 = "d".repeat(64),
                maxOutputTokens = MAX_REMOTE_PROVIDER_OUTPUT_TOKENS,
            )
        val scratch = temporaryDirectory.resolve("scratch")
        val durable = temporaryDirectory.resolve("durable")

        recorder.preflightEvidenceDestinations(scratch, durable)

        assertTrue(Files.isDirectory(scratch))
        val revisionDirectory = durable.resolve("a".repeat(40))
        assertTrue(Files.isDirectory(revisionDirectory))
        Files.list(scratch).use { entries -> assertEquals(0L, entries.count()) }
        Files.list(revisionDirectory).use { entries -> assertEquals(0L, entries.count()) }
    }

    @Test
    fun `smoke report preserves provenance call counts and billing truth without content`() {
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                codeRevision = "a".repeat(40),
                fixtureId = "live-bio-smoke-v2",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = "c".repeat(64),
                outputSchemaSha256 = "d".repeat(64),
                maxOutputTokens = 1_024,
                startedAt = Instant.parse("2026-07-20T00:00:00Z"),
            )
        recorder.recordInvocationStart()
        val diagnostics = LiveRemoteBioDiagnosticAccumulator()
        diagnostics.record(
            RemoteBioGenerationDiagnosticEvent(
                RemoteBioGenerationDiagnostic.VALID_TEMPLATE,
            ),
        )
        val validTemplate =
            validTemplate(
                "{{NAME}} makes {{HOBBY}} delightfully odd as a {{JOB}}.",
            )
        recorder.record(
            BioGenerationResult.Template(validTemplate),
        )
        recorder.recordGroundedBio(
            GeneratedBio.compose(
                validTemplate,
                BioGrounding(name = "N", jobTitle = "J", hobby = "H"),
            ),
        )
        recorder.recordInvocationStart()
        diagnostics.record(
            RemoteBioGenerationDiagnosticEvent(
                RemoteBioGenerationDiagnostic.PROVIDER_INVALID_OUTPUT,
            ),
        )
        recorder.record(BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT))

        val report =
            recorder.toSanitizedMap(
                providerRequestEvidence =
                    mapOf(
                        "request_count" to 2,
                        "all_requests_match_expected_configuration" to true,
                    ),
                providerResponseEvidence =
                    mapOf(
                        "attempt_count" to 2,
                        "response_count" to 2,
                        "usage_reported_response_count" to 2,
                        "input_tokens" to 400L,
                        "output_tokens" to 100L,
                        "total_tokens" to 500L,
                    ),
                applicationDiagnostics = diagnostics.summary(),
                delegatedHttpSendAttempts = 2,
                boundaryViolationCounts = emptyMap(),
                boundaryAssertionsCompleted = false,
                passed = false,
                completedAt = Instant.parse("2026-07-20T00:00:02Z"),
            )

        assertEquals(2, report["report_schema_version"])
        val provenance = report.getValue("provenance") as Map<*, *>
        assertEquals(1_024, provenance["max_output_tokens"])
        assertEquals(3, provenance["planned_calls"])
        val execution = report.getValue("execution") as Map<*, *>
        assertEquals(2, execution["model_provider_client_invocations"])
        assertEquals(2, execution["provider_result_count"])
        assertEquals(0, execution["invocations_without_result"])
        assertEquals(listOf("valid_prose", "invalid_output"), execution["normalized_result_sequence"])
        assertEquals(listOf(0L, 0L), execution["model_provider_client_latency_millis"])
        assertEquals(0, execution["invocations_without_latency"])
        assertEquals(0, execution["invocations_without_application_diagnostic"])
        val applicationDiagnostics =
            execution["application_generation_diagnostics"] as Map<*, *>
        assertEquals(
            listOf("valid_template", "provider_invalid_output"),
            applicationDiagnostics["diagnostic_sequence"],
        )
        assertEquals(31, execution["maximum_model_authored_code_points"])
        assertEquals(1, execution["maximum_sentence_count"])
        assertEquals(34, execution["maximum_final_grounded_code_points"])
        assertEquals(0, execution["valid_results_without_grounded_measurement"])
        assertEquals(2, execution["delegated_http_send_attempts"])
        assertEquals(
            mapOf(
                "request_count" to 2,
                "all_requests_match_expected_configuration" to true,
            ),
            execution["provider_request_evidence"],
        )
        assertEquals(1, execution["not_attempted_calls"])
        assertEquals(0, execution["retries"])
        assertEquals(0, execution["top_up_calls"])
        assertEquals(
            mapOf("invalid_output" to 1, "valid_prose" to 1),
            execution["normalized_result_counts"],
        )
        val billing = report.getValue("billing") as Map<*, *>
        assertNull(billing["actual_billed_usd"])
        assertTrue(billing["provider_usage_when_present_is_metering_evidence"] as Boolean)
        assertTrue(billing["provider_response_received"] as Boolean)
        assertTrue(billing["provider_usage_reported"] as Boolean)
        assertTrue(billing["metered_processing_evidenced"] as Boolean)
        val gate = report.getValue("gate") as Map<*, *>
        assertTrue(gate["evidence_complete_for_attempted_calls"] as Boolean)
        val rendered = report.toString()
        assertFalse(rendered.contains("{{NAME}}"))
        assertFalse(rendered.contains("delightfully odd"))
        assertFalse(rendered.contains("synthetic-secret"))
    }

    @Test
    fun `failed smoke report is written before the test result is surfaced`() {
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                codeRevision = "a".repeat(40),
                fixtureId = "live-bio-smoke-v2",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = "c".repeat(64),
                outputSchemaSha256 = "d".repeat(64),
                maxOutputTokens = 1_024,
                startedAt = Instant.parse("2026-07-20T00:00:00Z"),
            )
        recorder.recordInvocationStart()
        val diagnostics = LiveRemoteBioDiagnosticAccumulator()
        diagnostics.record(
            RemoteBioGenerationDiagnosticEvent(
                RemoteBioGenerationDiagnostic.PROVIDER_INVALID_OUTPUT,
            ),
        )
        recorder.record(BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT))

        val reportPath =
            recorder.write(
                objectMapper = tools.jackson.databind.json.JsonMapper.builder().build(),
                providerRequestEvidence =
                    mapOf(
                        "request_count" to 1,
                        "all_requests_match_expected_configuration" to true,
                    ),
                providerResponseEvidence =
                    mapOf(
                        "attempt_count" to 1,
                        "response_count" to 1,
                    ),
                applicationDiagnostics = diagnostics.summary(),
                delegatedHttpSendAttempts = 1,
                boundaryViolationCounts = emptyMap(),
                boundaryAssertionsCompleted = false,
                passed = false,
                reportDirectory = temporaryDirectory,
                durableReportDirectory = temporaryDirectory.resolve("durable"),
            )

        assertEquals(temporaryDirectory.resolve("report.json"), reportPath)
        val report = Files.readString(reportPath)
        val durableReportPath =
            temporaryDirectory
                .resolve("durable")
                .resolve("a".repeat(40))
                .resolve(
                    "2026-07-20T00-00-00Z-openai-" +
                        BioEvalHash.sha256("gpt-5.6-luna").take(12) +
                        "-report.json",
                )
        assertEquals(report, Files.readString(durableReportPath))
        assertTrue(report.contains("\"passed\" : false"))
        assertTrue(report.contains("\"not_attempted_calls\" : 2"))
        assertTrue(report.contains("\"metered_processing_evidenced\" : false"))
        assertFalse(report.contains("{{NAME}}"))
    }

    @Test
    fun `smoke report exposes an invocation that threw before returning a provider result`() {
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                codeRevision = "a".repeat(40),
                fixtureId = "live-bio-smoke-v2",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = "c".repeat(64),
                outputSchemaSha256 = "d".repeat(64),
                maxOutputTokens = MAX_REMOTE_PROVIDER_OUTPUT_TOKENS,
            )

        recorder.recordInvocationStart()
        recorder.recordInvocationFailure(elapsedNanos = 7_000_000L)
        val diagnostics = LiveRemoteBioDiagnosticAccumulator()
        val report =
            recorder.toSanitizedMap(
                providerRequestEvidence =
                    mapOf(
                        "request_count" to 1,
                        "all_requests_match_expected_configuration" to true,
                    ),
                providerResponseEvidence =
                    mapOf(
                        "attempt_count" to 1,
                        "response_count" to 1,
                    ),
                applicationDiagnostics = diagnostics.summary(),
                delegatedHttpSendAttempts = 1,
                boundaryViolationCounts = emptyMap(),
                boundaryAssertionsCompleted = false,
                passed = false,
            )

        val execution = report.getValue("execution") as Map<*, *>
        assertEquals(1, execution["model_provider_client_invocations"])
        assertEquals(0, execution["provider_result_count"])
        assertEquals(1, execution["invocations_without_result"])
        assertEquals(1, execution["harness_error_count"])
        assertEquals(listOf(7L), execution["model_provider_client_latency_millis"])
        assertEquals(0, execution["invocations_without_latency"])
        val gate = report.getValue("gate") as Map<*, *>
        assertFalse(gate["all_planned_calls_completed"] as Boolean)
        assertFalse(gate["zero_harness_errors"] as Boolean)
    }

    @Test
    fun `smoke gate cannot pass when provider evidence is incomplete`() {
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = "gpt-5.6-luna",
                codeRevision = "a".repeat(40),
                fixtureId = "live-bio-smoke-v2",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = "c".repeat(64),
                outputSchemaSha256 = "d".repeat(64),
                maxOutputTokens = MAX_REMOTE_PROVIDER_OUTPUT_TOKENS,
            )
        val diagnostics = LiveRemoteBioDiagnosticAccumulator()
        repeat(3) { index ->
            recorder.recordInvocationStart("case_$index")
            diagnostics.record(
                RemoteBioGenerationDiagnosticEvent(
                    RemoteBioGenerationDiagnostic.PROVIDER_INVALID_OUTPUT,
                ),
            )
            recorder.record(BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT))
        }

        val report =
            recorder.toSanitizedMap(
                providerRequestEvidence =
                    mapOf(
                        "request_count" to 2,
                        "all_requests_match_expected_configuration" to true,
                    ),
                providerResponseEvidence =
                    mapOf(
                        "attempt_count" to 3,
                        "response_count" to 3,
                    ),
                applicationDiagnostics = diagnostics.summary(),
                delegatedHttpSendAttempts = 3,
                boundaryViolationCounts = emptyMap(),
                boundaryAssertionsCompleted = true,
                passed = true,
            )

        val gate = report.getValue("gate") as Map<*, *>
        assertFalse(gate["evidence_complete_for_attempted_calls"] as Boolean)
        assertFalse(gate["all_planned_calls_completed"] as Boolean)
        assertFalse(gate["passed"] as Boolean)
    }

    @Test
    fun `credential free smoke orchestration spends all three calls on ordinary failures`() {
        val execution =
            runCredentialFreeOpenAiSmoke(
                label = "ordinary",
                response =
                    ProviderHttpResponse(
                        500,
                        """{"error":{"code":"server_error","type":"server_error"}}""",
                    ),
            )

        assertEquals(3, execution.fakeNetworkCalls)
        assertEquals(3, execution.controller.attemptCount)
        assertEquals("ordinary_results_failed", execution.controller.stopReason)
        assertEquals(
            List(3) { BioGenerationFailure.UNAVAILABLE },
            execution.controller.normalizedFailures,
        )
        assertTrue(execution.report.contains("\"not_attempted_calls\" : 0"))
        assertTrue(execution.report.contains("\"retries\" : 0"))
        assertTrue(execution.report.contains("\"top_up_calls\" : 0"))
        assertTrue(execution.report.contains("\"stop_reason\" : \"ordinary_results_failed\""))
    }

    @Test
    fun `credential free smoke orchestration stops after one terminal response`() {
        val terminalResponses =
            listOf(
                "authentication" to
                    ProviderHttpResponse(
                        401,
                        """{"error":{"code":"invalid_api_key","type":"authentication_error"}}""",
                    ),
                "permission" to ProviderHttpResponse(403, """{"error":{"type":"permission_error"}}"""),
                "not_found" to ProviderHttpResponse(404, """{"error":{"type":"not_found_error"}}"""),
                "invalid_request" to
                    ProviderHttpResponse(
                        400,
                        """{"error":{"code":"unsupported_parameter","type":"invalid_request_error"}}""",
                    ),
                "billing" to
                    ProviderHttpResponse(
                        429,
                        """{"error":{"code":"insufficient_quota","type":"insufficient_quota"}}""",
                    ),
            )

        terminalResponses.forEach { (expectedCategory, response) ->
            val execution =
                runCredentialFreeOpenAiSmoke(
                    label = "terminal-$expectedCategory",
                    response = response,
                )

            assertEquals(1, execution.fakeNetworkCalls, expectedCategory)
            assertEquals(1, execution.controller.attemptCount, expectedCategory)
            assertEquals("terminal_provider_failure", execution.controller.stopReason)
            assertEquals(
                expectedCategory,
                execution.controller.terminalProviderFailureCategory,
            )
            assertTrue(execution.report.contains("\"not_attempted_calls\" : 2"))
            assertTrue(execution.report.contains("\"retries\" : 0"))
            assertTrue(execution.report.contains("\"top_up_calls\" : 0"))
            assertTrue(execution.report.contains("\"stop_reason\" : \"terminal_provider_failure\""))
        }
    }

    @Test
    fun `credential free smoke orchestration stops before delegation on boundary failures`() {
        val security =
            runCredentialFreeOpenAiSmoke(
                label = "security",
                response = ProviderHttpResponse(200, "{}"),
                mutateAllowedHeaderValue = true,
            )
        assertEquals(0, security.fakeNetworkCalls)
        assertEquals(1, security.controller.attemptCount)
        assertEquals("security_boundary", security.controller.stopReason)
        assertTrue(security.report.contains("\"stop_reason\" : \"security_boundary\""))

        val preTransport =
            runCredentialFreeOpenAiSmoke(
                label = "pre-transport",
                response = ProviderHttpResponse(200, "{}"),
                failBeforeTransport = true,
            )
        assertEquals(0, preTransport.fakeNetworkCalls)
        assertEquals(1, preTransport.controller.attemptCount)
        assertEquals("pre_transport_failure", preTransport.controller.stopReason)
        assertTrue(preTransport.report.contains("\"stop_reason\" : \"pre_transport_failure\""))
    }

    private fun runCredentialFreeOpenAiSmoke(
        label: String,
        response: ProviderHttpResponse,
        mutateAllowedHeaderValue: Boolean = false,
        failBeforeTransport: Boolean = false,
    ): ControlledSmokeExecution {
        val objectMapper = JsonMapper.builder().build()
        val provider = LiveBioEvalProvider.OPENAI
        val model = "gpt-5.6-luna"
        val credential = "synthetic-secret"
        val request = BioEvalCorpusLoader.load().cases.first().toRequest()
        val fingerprint = captureApplicationRequestFingerprint(objectMapper, request)
        var fakeNetworkCalls = 0
        val inspector =
            InspectingProviderHttpTransport(
                delegate =
                    ProviderHttpTransport {
                        fakeNetworkCalls++
                        response
                    },
                provider = provider,
                exactModelId = model,
                expectedCredential = credential,
                maxCalls = 3,
                expectedFingerprint = fingerprint,
                objectMapper = objectMapper,
            )
        val transportForClient =
            if (mutateAllowedHeaderValue) {
                ProviderHttpTransport { original ->
                    inspector.send(
                        original.copyWithHeaders(
                            original.headers +
                                ("Authorization" to "Bearer RAW-SECRET-PROFILE-VALUE"),
                        ),
                    )
                }
            } else {
                inspector
            }
        val delegate =
            if (failBeforeTransport) {
                ModelProviderClient {
                    ModelProviderResult.Failure(BioGenerationFailure.TIMEOUT)
                }
            } else {
                provider.createClient(
                    credential = credential,
                    model = model,
                    objectMapper = objectMapper,
                    transport = transportForClient,
                )
            }
        val applicationInspector =
            InspectingModelProviderClient(
                delegate = delegate,
                objectMapper = objectMapper,
                expectedFingerprint = fingerprint,
            )
        val diagnostics = LiveRemoteBioDiagnosticAccumulator()
        val generator = RemoteBioGenerator(applicationInspector, objectMapper, diagnostics)
        val controller = LiveBioSmokeCallController()
        val recorder =
            LiveBioSmokeRecorder(
                provider = "openai",
                exactModelId = model,
                codeRevision = "a".repeat(40),
                fixtureId = "credential-free-$label",
                fixtureSha256 = "b".repeat(64),
                promptSha256 = fingerprint.promptSha256,
                outputSchemaSha256 = fingerprint.outputSchemaSha256,
                maxOutputTokens = fingerprint.maxOutputTokens,
                startedAt = Instant.parse("2026-07-20T00:00:00Z"),
            )
        val reportDirectory = temporaryDirectory.resolve(label).resolve("scratch")
        val durableDirectory = temporaryDirectory.resolve(label).resolve("durable")
        var boundaryViolations = emptyMap<String, Int>()
        try {
            for (index in 0 until 3) {
                val sendsBefore = inspector.delegatedHttpSendAttempts
                recorder.recordInvocationStart("case_$index")
                val result = generator.generate(request)
                recorder.record(result)
                boundaryViolations =
                    mergeCounts(
                        applicationInspector.violationCounts,
                        inspector.violationCounts,
                    )
                if (
                    !controller.observeAttempt(
                        result = result,
                        providerSendDelegated =
                            inspector.delegatedHttpSendAttempts == sendsBefore + 1,
                        boundaryViolationCount = boundaryViolations.values.sum(),
                        terminalFailureCategory =
                            inspector.terminalProviderFailureCategory,
                    )
                ) {
                    break
                }
            }
            controller.finish()
        } finally {
            recorder.write(
                objectMapper = objectMapper,
                providerRequestEvidence = inspector.providerRequestEvidence,
                providerResponseEvidence = inspector.providerResponseEvidence,
                applicationDiagnostics = diagnostics.summary(),
                delegatedHttpSendAttempts = inspector.delegatedHttpSendAttempts,
                boundaryViolationCounts = boundaryViolations,
                boundaryAssertionsCompleted = false,
                stopReason = controller.stopReason,
                terminalProviderFailureCategory =
                    controller.terminalProviderFailureCategory,
                passed = false,
                reportDirectory = reportDirectory,
                durableReportDirectory = durableDirectory,
            )
        }
        return ControlledSmokeExecution(
            controller = controller,
            fakeNetworkCalls = fakeNetworkCalls,
            report = Files.readString(reportDirectory.resolve("report.json")),
        )
    }

    private fun ProviderHttpRequest.copyWithHeaders(
        newHeaders: Map<String, String>,
    ): ProviderHttpRequest =
        ProviderHttpRequest(
            method = method,
            uri = uri,
            headers = newHeaders,
            body = body,
            timeout = timeout,
        )

    private data class ControlledSmokeExecution(
        val controller: LiveBioSmokeCallController,
        val fakeNetworkCalls: Int,
        val report: String,
    )

    private fun validTemplate(candidate: String): GeneratedBioTemplate =
        when (val result = GeneratedBioTemplate.validate(candidate)) {
            is BioGenerationResult.Template -> result.value
            is BioGenerationResult.Failure -> error("Fixture must be valid")
        }
}
