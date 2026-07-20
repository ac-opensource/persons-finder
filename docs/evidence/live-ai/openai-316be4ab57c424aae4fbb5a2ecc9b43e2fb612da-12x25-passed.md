# OpenAI live bio evaluation evidence

> Result: **PASS**

## What this evidence establishes

This is a fixed-corpus test of the application-owned structural and security contract for a nondeterministic provider response. It does not assert exact wording, subjective prose quality, or production-wide reliability.

The report is content-safe: it contains no prompt text, schema text, request or response body, model prose, profile value, credential, exception message, endpoint, or raw provider identifier.

The ignored JSON checkpoint retains the exhaustive sanitized machine facts. This review artifact keeps every aggregate and the key content-free validation, transport, and metering facts for every paid attempt without duplicating the raw machine record.

## Test identity

| Metric | Value |
|---|---|
| Report schema | 6 |
| Data policy | sanitized_metrics_no_request_or_response_content |
| Provider | openai |
| Exact model | gpt-5.6-luna |
| Clean code revision | 316be4ab57c424aae4fbb5a2ecc9b43e2fb612da |
| Started (UTC) | 2026-07-20T15:21:59.823180Z |
| Completed (UTC) | 2026-07-20T15:29:30.484573Z |
| Corpus ID | bio-cases-v1 |
| Corpus schema | 1 |
| Corpus SHA-256 | f34f0f3841f3170e1ebda035f172d24009c137ae270226077f45a82164d12e16 |
| Prompt SHA-256 | 7f5e462f26c1af60474d905a73373692160179652cf009f8596d6a97c9b5492c |
| Output schema SHA-256 | bc40a4627d1863e9702f7aa403fb7d2e44f888b04dc53691ae4d98724083a6a6 |
| Cases per round | 12 |
| Rounds | 25 |
| Planned calls | 300 |
| Case order | cyclic_rotation_v1 |
| Pacing strategy | minimum_attempt_start_interval_v1 |
| Minimum call interval (ms) | 0 |
| Maximum provider output tokens | 256 |
| Model-authored limit (code points) | 512 |
| Maximum source grounding (code points) | 220 |
| Final grounded limit (code points) | 732 |
| Application generation deadline (ms) | 15000 |

## Acceptance gate

| Metric | Value |
|---|---|
| execution_finalized | true |
| reliability_gate_configured | true |
| synthetic_retention_and_data_use_approved | true |
| hard_boundary_violations | 0 |
| harness_errors | 0 |
| grounded_evidence_complete | true |
| evidence_complete | true |
| all_planned_calls_completed | true |
| passed | true |
| maximum_one_sided_95_percent_wilson_upper_failure_bound | 0.01 |

## Aggregate result

| Metric | Value |
|---|---|
| attempts | 300 |
| valid_prose_count | 300 |
| distinct_valid_prose_count | 298 |
| deterministic_catalog_match_count | 0 |
| final_grounded_size_reported_count | 300 |
| maximum_final_grounded_code_points | 416 |
| valid_results_without_grounded_measurement | 0 |
| failure_count | 0 |
| observed_failure_rate | 0 |
| one_sided_95_percent_wilson_upper_failure_bound | 0.008937872 |
| result_counts.valid_prose | 300 |
| result_counts.timeout | 0 |
| result_counts.rate_limited | 0 |
| result_counts.unavailable | 0 |
| result_counts.invalid_output | 0 |
| result_counts.policy_rejected | 0 |
| result_counts.cancelled | 0 |
| result_counts.harness_error | 0 |
| latency_nanos.p50 | 1329510916 |
| latency_nanos.p95 | 2704556209 |
| latency_nanos.max | 6404460292 |

## Pacing

| Metric | Value |
|---|---|
| wait_event_count | 0 |
| actual_wait_nanos | 0 |

## Execution accounting

| Metric | Value |
|---|---|
| execution_finalized | true |
| model_provider_client_invocations | 300 |
| stop_reason | completed |
| terminal_provider_failure_category | — |
| delegated_http_send_attempts | 300 |
| attempted_calls | 300 |
| not_attempted_calls | 0 |
| retries | 0 |
| top_up_calls | 0 |
| provider_fallbacks | 0 |
| working_tree_clean | true |
| hard_boundary_violation_count | 0 |

## Billing evidence

| Metric | Value |
|---|---|
| provider_usage_when_present_is_metering_evidence | true |
| provider_response_received | true |
| provider_usage_reported | true |
| usage_reported_response_count | 300 |
| metered_processing_evidenced | true |
| actual_billed_usd | — |
| actual_billing_requires_provider_billing_export | true |

## Usage-cost estimate

| Metric | Value |
|---|---|
| Published input rate used (USD / 1M tokens) | 1 |
| Published output rate used (USD / 1M tokens) | 6 |
| Formula | (75150 x 1.0 + 14595 x 6.0) / 1,000,000 |
| Estimated usage (USD) | 0.162720 |
| Actual billed cost | Unavailable without billing export |
| Pricing basis | [OpenAI published standard API rates](https://developers.openai.com/api/docs/pricing) reviewed 2026-07-20 |
| Estimate limitation | Simple full-input/output-token estimate; actual billing can differ with caching, service tier, regional processing, or account adjustments |

## Request-boundary summary

| Metric | Value |
|---|---|
| request_count | 300 |
| request_body_bytes | 555525 |
| maximum_request_body_bytes | 1863 |
| maximum_timeout_millis | 14999 |
| maximum_system_instruction_utf8_bytes | 686 |
| maximum_input_payload_utf8_bytes | 244 |
| maximum_output_schema_utf8_bytes | 684 |
| max_output_token_values | 256 |
| reasoning_effort_counts.none | 300 |
| structured_output_mode_counts.json_schema | 300 |
| output_schema_fingerprint_reported_count | 300 |
| output_schema_fingerprint_match_count | 300 |
| placeholder_pattern_reported_count | 300 |
| placeholder_pattern_match_count | 300 |
| requests_with_explicit_sampling_configuration | 0 |
| requests_with_stop_sequences | 0 |
| unexpected_header_name_count | 0 |
| unexpected_configuration_field_count | 0 |
| expected_configuration_match_count | 300 |
| all_requests_match_expected_configuration | true |

## Provider response and usage summary

| Metric | Value |
|---|---|
| attempt_count | 300 |
| response_count | 300 |
| http_status_code_counts.200 | 300 |
| http_status_class_counts.2xx | 300 |
| response_body_bytes | 1145487 |
| maximum_response_body_bytes | 3886 |
| transport_latency_millis | 446920 |
| maximum_transport_latency_millis | 6402 |
| transport_failure_count | 0 |
| body_too_large_count | 0 |
| invalid_json_object_count | 0 |
| invalid_provider_envelope_count | 0 |
| provider_model_match_counts.matched | 300 |
| provider_status_counts.completed | 300 |
| service_tier_counts.default | 300 |
| safe_metadata_header_presence_counts.openai-processing-ms | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-limit-requests | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-limit-tokens | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-remaining-requests | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-remaining-tokens | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-reset-requests | 300 |
| safe_metadata_header_presence_counts.x-ratelimit-reset-tokens | 300 |
| diagnostic_counts.completed_structured_output | 300 |
| provider_request_id_count | 300 |
| unique_provider_request_id_count | 300 |
| provider_request_id_sequence_sha256 | 13a898b7e52dcdf05c92d6258f2c690058e779f1d4a9b91d32c3745cca094d29 |
| provider_response_id_count | 300 |
| unique_provider_response_id_count | 300 |
| provider_response_id_sequence_sha256 | 7d9a41384435779b21cb6e2da132f1dbc34620a34a14da384d77071b09d5d9cf |
| usage_reported_response_count | 300 |
| input_tokens_reported_response_count | 300 |
| input_tokens | 75150 |
| maximum_input_tokens_per_response | 253 |
| output_tokens_reported_response_count | 300 |
| output_tokens | 14595 |
| maximum_output_tokens_per_response | 64 |
| total_tokens_reported_or_derived_response_count | 300 |
| total_tokens | 89745 |
| maximum_total_tokens_per_response | 317 |
| derived_total_tokens_response_count | 0 |
| effective_input_tokens_reported_or_derived_response_count | 300 |
| effective_input_tokens | 75150 |
| maximum_effective_input_tokens_per_response | 253 |
| cached_input_tokens_reported_response_count | 300 |
| cached_input_tokens | 0 |
| maximum_cached_input_tokens_per_response | 0 |
| cache_write_tokens_reported_response_count | 300 |
| cache_write_tokens | 0 |
| maximum_cache_write_tokens_per_response | 0 |
| cache_creation_input_tokens_reported_response_count | 0 |
| cache_creation_input_tokens | 0 |
| maximum_cache_creation_input_tokens_per_response | 0 |
| cache_creation_five_minute_input_tokens_reported_response_count | 0 |
| cache_creation_five_minute_input_tokens | 0 |
| cache_creation_one_hour_input_tokens_reported_response_count | 0 |
| cache_creation_one_hour_input_tokens | 0 |
| reasoning_or_thinking_tokens_reported_response_count | 300 |
| reasoning_or_thinking_tokens | 0 |
| maximum_reasoning_or_thinking_tokens_per_response | 0 |
| tool_use_prompt_tokens_reported_response_count | 0 |
| tool_use_prompt_tokens | 0 |
| maximum_tool_use_prompt_tokens_per_response | 0 |
| provider_output_item_count | 300 |
| reasoning_output_item_count | 0 |
| output_text_item_count | 300 |
| invalid_output_text_item_count | 0 |
| provider_output_text_utf8_bytes | 51387 |
| maximum_provider_output_text_utf8_bytes_per_response | 239 |
| provider_output_text_code_points | 51387 |
| maximum_provider_output_text_code_points_per_response | 239 |
| refusal_item_count | 0 |
| safety_rating_count | 0 |
| usage_envelope_present_response_count | 300 |

## Application validation summary

| Metric | Value |
|---|---|
| diagnostic_count | 300 |
| diagnostic_counts.valid_template | 300 |
| output_json_size_reported_count | 300 |
| maximum_output_json_utf8_bytes | 239 |
| maximum_output_json_code_points | 239 |
| bio_template_size_reported_count | 300 |
| maximum_bio_template_code_points | 220 |
| model_authored_size_reported_count | 300 |
| maximum_model_authored_code_points | 196 |
| sentence_count_reported_count | 300 |
| maximum_sentence_count | 2 |

## Results by round

| Round | Rotation | Calls | Valid | Failures | Distinct | Input | Output | Max output | Max authored cp | Max grounded cp | Max sentences | Wilson upper | Transport p95 ms |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 | 12 | 12 | 0 | 12 | 3006 | 583 | 56 | 171 | 391 | 2 | 0.183981195 | 3826 |
| 2 | case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 | 12 | 12 | 0 | 12 | 3006 | 586 | 58 | 171 | 391 | 2 | 0.183981195 | 1557 |
| 3 | case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 | 12 | 12 | 0 | 12 | 3006 | 556 | 58 | 155 | 375 | 2 | 0.183981195 | 3004 |
| 4 | case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 | 12 | 12 | 0 | 12 | 3006 | 578 | 56 | 157 | 377 | 2 | 0.183981195 | 1689 |
| 5 | case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 | 12 | 12 | 0 | 12 | 3006 | 603 | 64 | 185 | 405 | 2 | 0.183981195 | 3809 |
| 6 | case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 | 12 | 12 | 0 | 12 | 3006 | 581 | 56 | 159 | 379 | 2 | 0.183981195 | 2640 |
| 7 | case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 | 12 | 12 | 0 | 12 | 3006 | 575 | 54 | 155 | 375 | 2 | 0.183981195 | 2671 |
| 8 | case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 | 12 | 12 | 0 | 12 | 3006 | 588 | 57 | 171 | 391 | 2 | 0.183981195 | 1496 |
| 9 | case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 | 12 | 12 | 0 | 12 | 3006 | 547 | 56 | 173 | 393 | 2 | 0.183981195 | 3093 |
| 10 | case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 | 12 | 12 | 0 | 12 | 3006 | 570 | 58 | 172 | 392 | 2 | 0.183981195 | 3073 |
| 11 | case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 | 12 | 12 | 0 | 12 | 3006 | 570 | 57 | 167 | 387 | 2 | 0.183981195 | 6228 |
| 12 | case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 | 12 | 12 | 0 | 12 | 3006 | 593 | 56 | 168 | 388 | 2 | 0.183981195 | 3422 |
| 13 | case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 | 12 | 12 | 0 | 12 | 3006 | 594 | 58 | 171 | 391 | 2 | 0.183981195 | 1474 |
| 14 | case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 | 12 | 12 | 0 | 12 | 3006 | 622 | 61 | 196 | 416 | 2 | 0.183981195 | 3352 |
| 15 | case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 | 12 | 12 | 0 | 12 | 3006 | 613 | 60 | 182 | 402 | 2 | 0.183981195 | 3463 |
| 16 | case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 | 12 | 12 | 0 | 12 | 3006 | 613 | 57 | 168 | 388 | 2 | 0.183981195 | 1606 |
| 17 | case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 | 12 | 12 | 0 | 12 | 3006 | 592 | 61 | 167 | 387 | 2 | 0.183981195 | 1412 |
| 18 | case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 | 12 | 12 | 0 | 12 | 3006 | 571 | 52 | 142 | 362 | 2 | 0.183981195 | 1530 |
| 19 | case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 | 12 | 12 | 0 | 12 | 3006 | 565 | 56 | 177 | 397 | 2 | 0.183981195 | 1625 |
| 20 | case-008 -> case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 | 12 | 12 | 0 | 12 | 3006 | 593 | 58 | 182 | 402 | 2 | 0.183981195 | 1522 |
| 21 | case-009 -> case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 | 12 | 12 | 0 | 12 | 3006 | 565 | 54 | 149 | 369 | 2 | 0.183981195 | 1427 |
| 22 | case-010 -> case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 | 12 | 12 | 0 | 12 | 3006 | 593 | 60 | 171 | 391 | 2 | 0.183981195 | 1543 |
| 23 | case-011 -> case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 | 12 | 12 | 0 | 12 | 3006 | 565 | 60 | 176 | 396 | 2 | 0.183981195 | 1629 |
| 24 | case-012 -> case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 | 12 | 12 | 0 | 12 | 3006 | 580 | 54 | 170 | 390 | 2 | 0.183981195 | 1518 |
| 25 | case-001 -> case-002 -> case-003 -> case-004 -> case-005 -> case-006 -> case-007 -> case-008 -> case-009 -> case-010 -> case-011 -> case-012 | 12 | 12 | 0 | 12 | 3006 | 599 | 56 | 168 | 388 | 2 | 0.183981195 | 6402 |

## Per-case results

| Case | Attempts | Valid | Failures | Distinct | Catalog | Max grounded cp | Missing grounded | Wilson upper | p50 ms | p95 ms | Max ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| case-001 | 25 | 25 | 0 | 25 | 0 | 391 | 0 | 0.097653506 | 1304 | 3353 | 3486 |
| case-002 | 25 | 25 | 0 | 24 | 0 | 388 | 0 | 0.097653506 | 1332 | 1690 | 2672 |
| case-003 | 25 | 25 | 0 | 25 | 0 | 374 | 0 | 0.097653506 | 1355 | 1959 | 3464 |
| case-004 | 25 | 25 | 0 | 25 | 0 | 377 | 0 | 0.097653506 | 1307 | 2641 | 3005 |
| case-005 | 25 | 25 | 0 | 25 | 0 | 371 | 0 | 0.097653506 | 1318 | 3811 | 6229 |
| case-006 | 25 | 25 | 0 | 24 | 0 | 393 | 0 | 0.097653506 | 1361 | 3074 | 4289 |
| case-007 | 25 | 25 | 0 | 25 | 0 | 391 | 0 | 0.097653506 | 1314 | 2095 | 2116 |
| case-008 | 25 | 25 | 0 | 25 | 0 | 400 | 0 | 0.097653506 | 1381 | 1822 | 3299 |
| case-009 | 25 | 25 | 0 | 25 | 0 | 382 | 0 | 0.097653506 | 1354 | 3423 | 6404 |
| case-010 | 25 | 25 | 0 | 25 | 0 | 416 | 0 | 0.097653506 | 1363 | 1537 | 3116 |
| case-011 | 25 | 25 | 0 | 25 | 0 | 402 | 0 | 0.097653506 | 1340 | 2041 | 3830 |
| case-012 | 25 | 25 | 0 | 25 | 0 | 402 | 0 | 0.097653506 | 1321 | 2401 | 2708 |

## Per-slice results

| Slice | Attempts | Valid | Failures | Distinct | Catalog | Max grounded cp | Missing grounded | Wilson upper | p50 ms | p95 ms | Max ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| both-other | 25 | 25 | 0 | 25 | 0 | 402 | 0 | 0.097653506 | 1321 | 2401 | 2708 |
| job-coverage | 300 | 300 | 0 | 298 | 0 | 416 | 0 | 0.008937872 | 1329 | 2704 | 6404 |
| multi-interest | 25 | 25 | 0 | 25 | 0 | 377 | 0 | 0.097653506 | 1307 | 2641 | 3005 |
| single-interest | 275 | 275 | 0 | 273 | 0 | 416 | 0 | 0.00974249 | 1332 | 2708 | 6404 |

## Per-attempt validation ledger

<details>
<summary>Show all 300 content-free attempt records</summary>

| Call | Round | Slot | Case | Slices | Result | Output class | Authored cp | Grounded cp | Sentences | Catalog | App diagnostic | N/J/H counts | ASCII | Request checks |
|---:|---:|---:|---|---|---|---|---:|---:|---:|---|---|---|---|---|
| 1 | 1 | 1 | case-001 | job-coverage, single-interest | valid_prose | output-001 | 136 | 356 | 2 | false | valid_template | 1/1/1 | true | true |
| 2 | 1 | 2 | case-002 | job-coverage, single-interest | valid_prose | output-002 | 108 | 328 | 1 | false | valid_template | 1/1/1 | true | true |
| 3 | 1 | 3 | case-003 | job-coverage, single-interest | valid_prose | output-003 | 137 | 357 | 2 | false | valid_template | 1/1/1 | true | true |
| 4 | 1 | 4 | case-004 | job-coverage, multi-interest | valid_prose | output-004 | 148 | 368 | 2 | false | valid_template | 1/1/1 | true | true |
| 5 | 1 | 5 | case-005 | job-coverage, single-interest | valid_prose | output-005 | 151 | 371 | 2 | false | valid_template | 1/1/1 | true | true |
| 6 | 1 | 6 | case-006 | job-coverage, single-interest | valid_prose | output-006 | 95 | 315 | 1 | false | valid_template | 1/1/1 | true | true |
| 7 | 1 | 7 | case-007 | job-coverage, single-interest | valid_prose | output-007 | 70 | 290 | 1 | false | valid_template | 1/1/1 | true | true |
| 8 | 1 | 8 | case-008 | job-coverage, single-interest | valid_prose | output-008 | 119 | 339 | 2 | false | valid_template | 1/1/1 | true | true |
| 9 | 1 | 9 | case-009 | job-coverage, single-interest | valid_prose | output-009 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 10 | 1 | 10 | case-010 | job-coverage, single-interest | valid_prose | output-010 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 11 | 1 | 11 | case-011 | job-coverage, single-interest | valid_prose | output-011 | 132 | 352 | 2 | false | valid_template | 1/1/1 | true | true |
| 12 | 1 | 12 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-012 | 137 | 357 | 2 | false | valid_template | 1/1/1 | true | true |
| 13 | 2 | 1 | case-002 | job-coverage, single-interest | valid_prose | output-013 | 112 | 332 | 2 | false | valid_template | 1/1/1 | true | true |
| 14 | 2 | 2 | case-003 | job-coverage, single-interest | valid_prose | output-014 | 131 | 351 | 2 | false | valid_template | 1/1/1 | true | true |
| 15 | 2 | 3 | case-004 | job-coverage, multi-interest | valid_prose | output-015 | 124 | 344 | 2 | false | valid_template | 1/1/1 | true | true |
| 16 | 2 | 4 | case-005 | job-coverage, single-interest | valid_prose | output-016 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 17 | 2 | 5 | case-006 | job-coverage, single-interest | valid_prose | output-017 | 110 | 330 | 2 | false | valid_template | 1/1/1 | true | true |
| 18 | 2 | 6 | case-007 | job-coverage, single-interest | valid_prose | output-018 | 96 | 316 | 1 | false | valid_template | 1/1/1 | true | true |
| 19 | 2 | 7 | case-008 | job-coverage, single-interest | valid_prose | output-019 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 20 | 2 | 8 | case-009 | job-coverage, single-interest | valid_prose | output-020 | 107 | 327 | 2 | false | valid_template | 1/1/1 | true | true |
| 21 | 2 | 9 | case-010 | job-coverage, single-interest | valid_prose | output-021 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 22 | 2 | 10 | case-011 | job-coverage, single-interest | valid_prose | output-022 | 168 | 388 | 2 | false | valid_template | 1/1/1 | true | true |
| 23 | 2 | 11 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-023 | 87 | 307 | 1 | false | valid_template | 1/1/1 | true | true |
| 24 | 2 | 12 | case-001 | job-coverage, single-interest | valid_prose | output-024 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 25 | 3 | 1 | case-003 | job-coverage, single-interest | valid_prose | output-025 | 135 | 355 | 2 | false | valid_template | 1/1/1 | true | true |
| 26 | 3 | 2 | case-004 | job-coverage, multi-interest | valid_prose | output-026 | 118 | 338 | 2 | false | valid_template | 1/1/1 | true | true |
| 27 | 3 | 3 | case-005 | job-coverage, single-interest | valid_prose | output-027 | 135 | 355 | 2 | false | valid_template | 1/1/1 | true | true |
| 28 | 3 | 4 | case-006 | job-coverage, single-interest | valid_prose | output-028 | 76 | 296 | 1 | false | valid_template | 1/1/1 | true | true |
| 29 | 3 | 5 | case-007 | job-coverage, single-interest | valid_prose | output-029 | 145 | 365 | 2 | false | valid_template | 1/1/1 | true | true |
| 30 | 3 | 6 | case-008 | job-coverage, single-interest | valid_prose | output-030 | 82 | 302 | 1 | false | valid_template | 1/1/1 | true | true |
| 31 | 3 | 7 | case-009 | job-coverage, single-interest | valid_prose | output-031 | 115 | 335 | 2 | false | valid_template | 1/1/1 | true | true |
| 32 | 3 | 8 | case-010 | job-coverage, single-interest | valid_prose | output-032 | 95 | 315 | 1 | false | valid_template | 1/1/1 | true | true |
| 33 | 3 | 9 | case-011 | job-coverage, single-interest | valid_prose | output-033 | 84 | 304 | 1 | false | valid_template | 1/1/1 | true | true |
| 34 | 3 | 10 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-034 | 110 | 330 | 1 | false | valid_template | 1/1/1 | true | true |
| 35 | 3 | 11 | case-001 | job-coverage, single-interest | valid_prose | output-035 | 126 | 346 | 2 | false | valid_template | 1/1/1 | true | true |
| 36 | 3 | 12 | case-002 | job-coverage, single-interest | valid_prose | output-036 | 155 | 375 | 2 | false | valid_template | 1/1/1 | true | true |
| 37 | 4 | 1 | case-004 | job-coverage, multi-interest | valid_prose | output-037 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 38 | 4 | 2 | case-005 | job-coverage, single-interest | valid_prose | output-038 | 133 | 353 | 2 | false | valid_template | 1/1/1 | true | true |
| 39 | 4 | 3 | case-006 | job-coverage, single-interest | valid_prose | output-039 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 40 | 4 | 4 | case-007 | job-coverage, single-interest | valid_prose | output-040 | 118 | 338 | 2 | false | valid_template | 1/1/1 | true | true |
| 41 | 4 | 5 | case-008 | job-coverage, single-interest | valid_prose | output-041 | 157 | 377 | 2 | false | valid_template | 1/1/1 | true | true |
| 42 | 4 | 6 | case-009 | job-coverage, single-interest | valid_prose | output-042 | 106 | 326 | 2 | false | valid_template | 1/1/1 | true | true |
| 43 | 4 | 7 | case-010 | job-coverage, single-interest | valid_prose | output-043 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 44 | 4 | 8 | case-011 | job-coverage, single-interest | valid_prose | output-044 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 45 | 4 | 9 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-045 | 78 | 298 | 1 | false | valid_template | 1/1/1 | true | true |
| 46 | 4 | 10 | case-001 | job-coverage, single-interest | valid_prose | output-046 | 120 | 340 | 2 | false | valid_template | 1/1/1 | true | true |
| 47 | 4 | 11 | case-002 | job-coverage, single-interest | valid_prose | output-047 | 119 | 339 | 2 | false | valid_template | 1/1/1 | true | true |
| 48 | 4 | 12 | case-003 | job-coverage, single-interest | valid_prose | output-048 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 49 | 5 | 1 | case-005 | job-coverage, single-interest | valid_prose | output-049 | 110 | 330 | 1 | false | valid_template | 1/1/1 | true | true |
| 50 | 5 | 2 | case-006 | job-coverage, single-interest | valid_prose | output-050 | 79 | 299 | 1 | false | valid_template | 1/1/1 | true | true |
| 51 | 5 | 3 | case-007 | job-coverage, single-interest | valid_prose | output-051 | 146 | 366 | 2 | false | valid_template | 1/1/1 | true | true |
| 52 | 5 | 4 | case-008 | job-coverage, single-interest | valid_prose | output-052 | 155 | 375 | 2 | false | valid_template | 1/1/1 | true | true |
| 53 | 5 | 5 | case-009 | job-coverage, single-interest | valid_prose | output-053 | 147 | 367 | 2 | false | valid_template | 1/1/1 | true | true |
| 54 | 5 | 6 | case-010 | job-coverage, single-interest | valid_prose | output-054 | 185 | 405 | 2 | false | valid_template | 1/1/1 | true | true |
| 55 | 5 | 7 | case-011 | job-coverage, single-interest | valid_prose | output-055 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 56 | 5 | 8 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-056 | 172 | 392 | 2 | false | valid_template | 1/1/1 | true | true |
| 57 | 5 | 9 | case-001 | job-coverage, single-interest | valid_prose | output-057 | 131 | 351 | 2 | false | valid_template | 1/1/1 | true | true |
| 58 | 5 | 10 | case-002 | job-coverage, single-interest | valid_prose | output-058 | 151 | 371 | 2 | false | valid_template | 1/1/1 | true | true |
| 59 | 5 | 11 | case-003 | job-coverage, single-interest | valid_prose | output-059 | 122 | 342 | 2 | false | valid_template | 1/1/1 | true | true |
| 60 | 5 | 12 | case-004 | job-coverage, multi-interest | valid_prose | output-060 | 87 | 307 | 1 | false | valid_template | 1/1/1 | true | true |
| 61 | 6 | 1 | case-006 | job-coverage, single-interest | valid_prose | output-061 | 99 | 319 | 1 | false | valid_template | 1/1/1 | true | true |
| 62 | 6 | 2 | case-007 | job-coverage, single-interest | valid_prose | output-062 | 107 | 327 | 1 | false | valid_template | 1/1/1 | true | true |
| 63 | 6 | 3 | case-008 | job-coverage, single-interest | valid_prose | output-063 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 64 | 6 | 4 | case-009 | job-coverage, single-interest | valid_prose | output-064 | 128 | 348 | 2 | false | valid_template | 1/1/1 | true | true |
| 65 | 6 | 5 | case-010 | job-coverage, single-interest | valid_prose | output-065 | 145 | 365 | 2 | false | valid_template | 1/1/1 | true | true |
| 66 | 6 | 6 | case-011 | job-coverage, single-interest | valid_prose | output-066 | 156 | 376 | 2 | false | valid_template | 1/1/1 | true | true |
| 67 | 6 | 7 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-067 | 159 | 379 | 2 | false | valid_template | 1/1/1 | true | true |
| 68 | 6 | 8 | case-001 | job-coverage, single-interest | valid_prose | output-068 | 152 | 372 | 2 | false | valid_template | 1/1/1 | true | true |
| 69 | 6 | 9 | case-002 | job-coverage, single-interest | valid_prose | output-069 | 89 | 309 | 1 | false | valid_template | 1/1/1 | true | true |
| 70 | 6 | 10 | case-003 | job-coverage, single-interest | valid_prose | output-070 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 71 | 6 | 11 | case-004 | job-coverage, multi-interest | valid_prose | output-071 | 120 | 340 | 2 | false | valid_template | 1/1/1 | true | true |
| 72 | 6 | 12 | case-005 | job-coverage, single-interest | valid_prose | output-072 | 109 | 329 | 1 | false | valid_template | 1/1/1 | true | true |
| 73 | 7 | 1 | case-007 | job-coverage, single-interest | valid_prose | output-073 | 143 | 363 | 2 | false | valid_template | 1/1/1 | true | true |
| 74 | 7 | 2 | case-008 | job-coverage, single-interest | valid_prose | output-074 | 155 | 375 | 2 | false | valid_template | 1/1/1 | true | true |
| 75 | 7 | 3 | case-009 | job-coverage, single-interest | valid_prose | output-075 | 113 | 333 | 2 | false | valid_template | 1/1/1 | true | true |
| 76 | 7 | 4 | case-010 | job-coverage, single-interest | valid_prose | output-076 | 155 | 375 | 2 | false | valid_template | 1/1/1 | true | true |
| 77 | 7 | 5 | case-011 | job-coverage, single-interest | valid_prose | output-077 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 78 | 7 | 6 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-078 | 90 | 310 | 1 | false | valid_template | 1/1/1 | true | true |
| 79 | 7 | 7 | case-001 | job-coverage, single-interest | valid_prose | output-079 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 80 | 7 | 8 | case-002 | job-coverage, single-interest | valid_prose | output-080 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 81 | 7 | 9 | case-003 | job-coverage, single-interest | valid_prose | output-081 | 133 | 353 | 2 | false | valid_template | 1/1/1 | true | true |
| 82 | 7 | 10 | case-004 | job-coverage, multi-interest | valid_prose | output-082 | 122 | 342 | 2 | false | valid_template | 1/1/1 | true | true |
| 83 | 7 | 11 | case-005 | job-coverage, single-interest | valid_prose | output-083 | 88 | 308 | 1 | false | valid_template | 1/1/1 | true | true |
| 84 | 7 | 12 | case-006 | job-coverage, single-interest | valid_prose | output-084 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 85 | 8 | 1 | case-008 | job-coverage, single-interest | valid_prose | output-085 | 85 | 305 | 1 | false | valid_template | 1/1/1 | true | true |
| 86 | 8 | 2 | case-009 | job-coverage, single-interest | valid_prose | output-086 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 87 | 8 | 3 | case-010 | job-coverage, single-interest | valid_prose | output-087 | 148 | 368 | 2 | false | valid_template | 1/1/1 | true | true |
| 88 | 8 | 4 | case-011 | job-coverage, single-interest | valid_prose | output-088 | 100 | 320 | 1 | false | valid_template | 1/1/1 | true | true |
| 89 | 8 | 5 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-089 | 155 | 375 | 2 | false | valid_template | 1/1/1 | true | true |
| 90 | 8 | 6 | case-001 | job-coverage, single-interest | valid_prose | output-090 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 91 | 8 | 7 | case-002 | job-coverage, single-interest | valid_prose | output-091 | 105 | 325 | 1 | false | valid_template | 1/1/1 | true | true |
| 92 | 8 | 8 | case-003 | job-coverage, single-interest | valid_prose | output-092 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 93 | 8 | 9 | case-004 | job-coverage, multi-interest | valid_prose | output-093 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 94 | 8 | 10 | case-005 | job-coverage, single-interest | valid_prose | output-094 | 132 | 352 | 2 | false | valid_template | 1/1/1 | true | true |
| 95 | 8 | 11 | case-006 | job-coverage, single-interest | valid_prose | output-095 | 114 | 334 | 1 | false | valid_template | 1/1/1 | true | true |
| 96 | 8 | 12 | case-007 | job-coverage, single-interest | valid_prose | output-096 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 97 | 9 | 1 | case-009 | job-coverage, single-interest | valid_prose | output-097 | 65 | 285 | 1 | false | valid_template | 1/1/1 | true | true |
| 98 | 9 | 2 | case-010 | job-coverage, single-interest | valid_prose | output-098 | 105 | 325 | 1 | false | valid_template | 1/1/1 | true | true |
| 99 | 9 | 3 | case-011 | job-coverage, single-interest | valid_prose | output-099 | 138 | 358 | 2 | false | valid_template | 1/1/1 | true | true |
| 100 | 9 | 4 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-100 | 149 | 369 | 2 | false | valid_template | 1/1/1 | true | true |
| 101 | 9 | 5 | case-001 | job-coverage, single-interest | valid_prose | output-101 | 128 | 348 | 2 | false | valid_template | 1/1/1 | true | true |
| 102 | 9 | 6 | case-002 | job-coverage, single-interest | valid_prose | output-102 | 101 | 321 | 1 | false | valid_template | 1/1/1 | true | true |
| 103 | 9 | 7 | case-003 | job-coverage, single-interest | valid_prose | output-103 | 136 | 356 | 2 | false | valid_template | 1/1/1 | true | true |
| 104 | 9 | 8 | case-004 | job-coverage, multi-interest | valid_prose | output-104 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 105 | 9 | 9 | case-005 | job-coverage, single-interest | valid_prose | output-105 | 111 | 331 | 1 | false | valid_template | 1/1/1 | true | true |
| 106 | 9 | 10 | case-006 | job-coverage, single-interest | valid_prose | output-106 | 173 | 393 | 2 | false | valid_template | 1/1/1 | true | true |
| 107 | 9 | 11 | case-007 | job-coverage, single-interest | valid_prose | output-107 | 82 | 302 | 1 | false | valid_template | 1/1/1 | true | true |
| 108 | 9 | 12 | case-008 | job-coverage, single-interest | valid_prose | output-108 | 94 | 314 | 1 | false | valid_template | 1/1/1 | true | true |
| 109 | 10 | 1 | case-010 | job-coverage, single-interest | valid_prose | output-109 | 139 | 359 | 2 | false | valid_template | 1/1/1 | true | true |
| 110 | 10 | 2 | case-011 | job-coverage, single-interest | valid_prose | output-110 | 103 | 323 | 1 | false | valid_template | 1/1/1 | true | true |
| 111 | 10 | 3 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-111 | 146 | 366 | 2 | false | valid_template | 1/1/1 | true | true |
| 112 | 10 | 4 | case-001 | job-coverage, single-interest | valid_prose | output-112 | 112 | 332 | 2 | false | valid_template | 1/1/1 | true | true |
| 113 | 10 | 5 | case-002 | job-coverage, single-interest | valid_prose | output-113 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 114 | 10 | 6 | case-003 | job-coverage, single-interest | valid_prose | output-114 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 115 | 10 | 7 | case-004 | job-coverage, multi-interest | valid_prose | output-115 | 92 | 312 | 1 | false | valid_template | 1/1/1 | true | true |
| 116 | 10 | 8 | case-005 | job-coverage, single-interest | valid_prose | output-116 | 110 | 330 | 1 | false | valid_template | 1/1/1 | true | true |
| 117 | 10 | 9 | case-006 | job-coverage, single-interest | valid_prose | output-117 | 72 | 292 | 1 | false | valid_template | 1/1/1 | true | true |
| 118 | 10 | 10 | case-007 | job-coverage, single-interest | valid_prose | output-118 | 143 | 363 | 2 | false | valid_template | 1/1/1 | true | true |
| 119 | 10 | 11 | case-008 | job-coverage, single-interest | valid_prose | output-119 | 172 | 392 | 2 | false | valid_template | 1/1/1 | true | true |
| 120 | 10 | 12 | case-009 | job-coverage, single-interest | valid_prose | output-120 | 112 | 332 | 2 | false | valid_template | 1/1/1 | true | true |
| 121 | 11 | 1 | case-011 | job-coverage, single-interest | valid_prose | output-121 | 165 | 385 | 2 | false | valid_template | 1/1/1 | true | true |
| 122 | 11 | 2 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-122 | 123 | 343 | 2 | false | valid_template | 1/1/1 | true | true |
| 123 | 11 | 3 | case-001 | job-coverage, single-interest | valid_prose | output-123 | 134 | 354 | 2 | false | valid_template | 1/1/1 | true | true |
| 124 | 11 | 4 | case-002 | job-coverage, single-interest | valid_prose | output-124 | 80 | 300 | 1 | false | valid_template | 1/1/1 | true | true |
| 125 | 11 | 5 | case-003 | job-coverage, single-interest | valid_prose | output-125 | 140 | 360 | 2 | false | valid_template | 1/1/1 | true | true |
| 126 | 11 | 6 | case-004 | job-coverage, multi-interest | valid_prose | output-126 | 137 | 357 | 1 | false | valid_template | 1/1/1 | true | true |
| 127 | 11 | 7 | case-005 | job-coverage, single-interest | valid_prose | output-127 | 115 | 335 | 1 | false | valid_template | 1/1/1 | true | true |
| 128 | 11 | 8 | case-006 | job-coverage, single-interest | valid_prose | output-128 | 104 | 324 | 1 | false | valid_template | 1/1/1 | true | true |
| 129 | 11 | 9 | case-007 | job-coverage, single-interest | valid_prose | output-129 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 130 | 11 | 10 | case-008 | job-coverage, single-interest | valid_prose | output-130 | 80 | 300 | 1 | false | valid_template | 1/1/1 | true | true |
| 131 | 11 | 11 | case-009 | job-coverage, single-interest | valid_prose | output-131 | 116 | 336 | 2 | false | valid_template | 1/1/1 | true | true |
| 132 | 11 | 12 | case-010 | job-coverage, single-interest | valid_prose | output-132 | 167 | 387 | 2 | false | valid_template | 1/1/1 | true | true |
| 133 | 12 | 1 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-133 | 111 | 331 | 1 | false | valid_template | 1/1/1 | true | true |
| 134 | 12 | 2 | case-001 | job-coverage, single-interest | valid_prose | output-134 | 110 | 330 | 2 | false | valid_template | 1/1/1 | true | true |
| 135 | 12 | 3 | case-002 | job-coverage, single-interest | valid_prose | output-135 | 168 | 388 | 2 | false | valid_template | 1/1/1 | true | true |
| 136 | 12 | 4 | case-003 | job-coverage, single-interest | valid_prose | output-136 | 145 | 365 | 2 | false | valid_template | 1/1/1 | true | true |
| 137 | 12 | 5 | case-004 | job-coverage, multi-interest | valid_prose | output-137 | 120 | 340 | 2 | false | valid_template | 1/1/1 | true | true |
| 138 | 12 | 6 | case-005 | job-coverage, single-interest | valid_prose | output-138 | 135 | 355 | 2 | false | valid_template | 1/1/1 | true | true |
| 139 | 12 | 7 | case-006 | job-coverage, single-interest | valid_prose | output-139 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 140 | 12 | 8 | case-007 | job-coverage, single-interest | valid_prose | output-140 | 143 | 363 | 2 | false | valid_template | 1/1/1 | true | true |
| 141 | 12 | 9 | case-008 | job-coverage, single-interest | valid_prose | output-141 | 165 | 385 | 2 | false | valid_template | 1/1/1 | true | true |
| 142 | 12 | 10 | case-009 | job-coverage, single-interest | valid_prose | output-142 | 72 | 292 | 1 | false | valid_template | 1/1/1 | true | true |
| 143 | 12 | 11 | case-010 | job-coverage, single-interest | valid_prose | output-143 | 164 | 384 | 2 | false | valid_template | 1/1/1 | true | true |
| 144 | 12 | 12 | case-011 | job-coverage, single-interest | valid_prose | output-144 | 154 | 374 | 2 | false | valid_template | 1/1/1 | true | true |
| 145 | 13 | 1 | case-001 | job-coverage, single-interest | valid_prose | output-145 | 121 | 341 | 2 | false | valid_template | 1/1/1 | true | true |
| 146 | 13 | 2 | case-002 | job-coverage, single-interest | valid_prose | output-146 | 157 | 377 | 2 | false | valid_template | 1/1/1 | true | true |
| 147 | 13 | 3 | case-003 | job-coverage, single-interest | valid_prose | output-147 | 154 | 374 | 2 | false | valid_template | 1/1/1 | true | true |
| 148 | 13 | 4 | case-004 | job-coverage, multi-interest | valid_prose | output-148 | 89 | 309 | 1 | false | valid_template | 1/1/1 | true | true |
| 149 | 13 | 5 | case-005 | job-coverage, single-interest | valid_prose | output-149 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 150 | 13 | 6 | case-006 | job-coverage, single-interest | valid_prose | output-150 | 101 | 321 | 1 | false | valid_template | 1/1/1 | true | true |
| 151 | 13 | 7 | case-007 | job-coverage, single-interest | valid_prose | output-151 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 152 | 13 | 8 | case-008 | job-coverage, single-interest | valid_prose | output-152 | 79 | 299 | 1 | false | valid_template | 1/1/1 | true | true |
| 153 | 13 | 9 | case-009 | job-coverage, single-interest | valid_prose | output-153 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 154 | 13 | 10 | case-010 | job-coverage, single-interest | valid_prose | output-154 | 166 | 386 | 2 | false | valid_template | 1/1/1 | true | true |
| 155 | 13 | 11 | case-011 | job-coverage, single-interest | valid_prose | output-155 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 156 | 13 | 12 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-156 | 128 | 348 | 2 | false | valid_template | 1/1/1 | true | true |
| 157 | 14 | 1 | case-002 | job-coverage, single-interest | valid_prose | output-157 | 143 | 363 | 2 | false | valid_template | 1/1/1 | true | true |
| 158 | 14 | 2 | case-003 | job-coverage, single-interest | valid_prose | output-158 | 138 | 358 | 2 | false | valid_template | 1/1/1 | true | true |
| 159 | 14 | 3 | case-004 | job-coverage, multi-interest | valid_prose | output-159 | 118 | 338 | 1 | false | valid_template | 1/1/1 | true | true |
| 160 | 14 | 4 | case-005 | job-coverage, single-interest | valid_prose | output-160 | 111 | 331 | 1 | false | valid_template | 1/1/1 | true | true |
| 161 | 14 | 5 | case-006 | job-coverage, single-interest | valid_prose | output-161 | 135 | 355 | 1 | false | valid_template | 1/1/1 | true | true |
| 162 | 14 | 6 | case-007 | job-coverage, single-interest | valid_prose | output-162 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 163 | 14 | 7 | case-008 | job-coverage, single-interest | valid_prose | output-163 | 157 | 377 | 2 | false | valid_template | 1/1/1 | true | true |
| 164 | 14 | 8 | case-009 | job-coverage, single-interest | valid_prose | output-164 | 119 | 339 | 2 | false | valid_template | 1/1/1 | true | true |
| 165 | 14 | 9 | case-010 | job-coverage, single-interest | valid_prose | output-165 | 196 | 416 | 2 | false | valid_template | 1/1/1 | true | true |
| 166 | 14 | 10 | case-011 | job-coverage, single-interest | valid_prose | output-166 | 138 | 358 | 2 | false | valid_template | 1/1/1 | true | true |
| 167 | 14 | 11 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-167 | 147 | 367 | 2 | false | valid_template | 1/1/1 | true | true |
| 168 | 14 | 12 | case-001 | job-coverage, single-interest | valid_prose | output-168 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 169 | 15 | 1 | case-003 | job-coverage, single-interest | valid_prose | output-169 | 151 | 371 | 2 | false | valid_template | 1/1/1 | true | true |
| 170 | 15 | 2 | case-004 | job-coverage, multi-interest | valid_prose | output-170 | 157 | 377 | 2 | false | valid_template | 1/1/1 | true | true |
| 171 | 15 | 3 | case-005 | job-coverage, single-interest | valid_prose | output-171 | 121 | 341 | 2 | false | valid_template | 1/1/1 | true | true |
| 172 | 15 | 4 | case-006 | job-coverage, single-interest | valid_prose | output-172 | 137 | 357 | 2 | false | valid_template | 1/1/1 | true | true |
| 173 | 15 | 5 | case-007 | job-coverage, single-interest | valid_prose | output-173 | 146 | 366 | 2 | false | valid_template | 1/1/1 | true | true |
| 174 | 15 | 6 | case-008 | job-coverage, single-interest | valid_prose | output-174 | 69 | 289 | 1 | false | valid_template | 1/1/1 | true | true |
| 175 | 15 | 7 | case-009 | job-coverage, single-interest | valid_prose | output-175 | 123 | 343 | 2 | false | valid_template | 1/1/1 | true | true |
| 176 | 15 | 8 | case-010 | job-coverage, single-interest | valid_prose | output-176 | 177 | 397 | 2 | false | valid_template | 1/1/1 | true | true |
| 177 | 15 | 9 | case-011 | job-coverage, single-interest | valid_prose | output-177 | 134 | 354 | 2 | false | valid_template | 1/1/1 | true | true |
| 178 | 15 | 10 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-178 | 182 | 402 | 2 | false | valid_template | 1/1/1 | true | true |
| 179 | 15 | 11 | case-001 | job-coverage, single-interest | valid_prose | output-179 | 135 | 355 | 2 | false | valid_template | 1/1/1 | true | true |
| 180 | 15 | 12 | case-002 | job-coverage, single-interest | valid_prose | output-180 | 149 | 369 | 2 | false | valid_template | 1/1/1 | true | true |
| 181 | 16 | 1 | case-004 | job-coverage, multi-interest | valid_prose | output-181 | 66 | 286 | 1 | false | valid_template | 1/1/1 | true | true |
| 182 | 16 | 2 | case-005 | job-coverage, single-interest | valid_prose | output-182 | 122 | 342 | 2 | false | valid_template | 1/1/1 | true | true |
| 183 | 16 | 3 | case-006 | job-coverage, single-interest | valid_prose | output-183 | 135 | 355 | 2 | false | valid_template | 1/1/1 | true | true |
| 184 | 16 | 4 | case-007 | job-coverage, single-interest | valid_prose | output-184 | 133 | 353 | 2 | false | valid_template | 1/1/1 | true | true |
| 185 | 16 | 5 | case-008 | job-coverage, single-interest | valid_prose | output-185 | 158 | 378 | 2 | false | valid_template | 1/1/1 | true | true |
| 186 | 16 | 6 | case-009 | job-coverage, single-interest | valid_prose | output-186 | 156 | 376 | 2 | false | valid_template | 1/1/1 | true | true |
| 187 | 16 | 7 | case-010 | job-coverage, single-interest | valid_prose | output-187 | 168 | 388 | 2 | false | valid_template | 1/1/1 | true | true |
| 188 | 16 | 8 | case-011 | job-coverage, single-interest | valid_prose | output-188 | 150 | 370 | 2 | false | valid_template | 1/1/1 | true | true |
| 189 | 16 | 9 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-189 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 190 | 16 | 10 | case-001 | job-coverage, single-interest | valid_prose | output-190 | 139 | 359 | 2 | false | valid_template | 1/1/1 | true | true |
| 191 | 16 | 11 | case-002 | job-coverage, single-interest | valid_prose | output-191 | 152 | 372 | 2 | false | valid_template | 1/1/1 | true | true |
| 192 | 16 | 12 | case-003 | job-coverage, single-interest | valid_prose | output-192 | 146 | 366 | 2 | false | valid_template | 1/1/1 | true | true |
| 193 | 17 | 1 | case-005 | job-coverage, single-interest | valid_prose | output-193 | 59 | 279 | 1 | false | valid_template | 1/1/1 | true | true |
| 194 | 17 | 2 | case-006 | job-coverage, single-interest | valid_prose | output-194 | 106 | 326 | 1 | false | valid_template | 1/1/1 | true | true |
| 195 | 17 | 3 | case-007 | job-coverage, single-interest | valid_prose | output-195 | 149 | 369 | 2 | false | valid_template | 1/1/1 | true | true |
| 196 | 17 | 4 | case-008 | job-coverage, single-interest | valid_prose | output-196 | 167 | 387 | 2 | false | valid_template | 1/1/1 | true | true |
| 197 | 17 | 5 | case-009 | job-coverage, single-interest | valid_prose | output-197 | 139 | 359 | 2 | false | valid_template | 1/1/1 | true | true |
| 198 | 17 | 6 | case-010 | job-coverage, single-interest | valid_prose | output-198 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 199 | 17 | 7 | case-011 | job-coverage, single-interest | valid_prose | output-199 | 117 | 337 | 2 | false | valid_template | 1/1/1 | true | true |
| 200 | 17 | 8 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-200 | 149 | 369 | 2 | false | valid_template | 1/1/1 | true | true |
| 201 | 17 | 9 | case-001 | job-coverage, single-interest | valid_prose | output-201 | 141 | 361 | 2 | false | valid_template | 1/1/1 | true | true |
| 202 | 17 | 10 | case-002 | job-coverage, single-interest | valid_prose | output-202 | 118 | 338 | 2 | false | valid_template | 1/1/1 | true | true |
| 203 | 17 | 11 | case-003 | job-coverage, single-interest | valid_prose | output-203 | 132 | 352 | 2 | false | valid_template | 1/1/1 | true | true |
| 204 | 17 | 12 | case-004 | job-coverage, multi-interest | valid_prose | output-204 | 146 | 366 | 2 | false | valid_template | 1/1/1 | true | true |
| 205 | 18 | 1 | case-006 | job-coverage, single-interest | valid_prose | output-150 | 101 | 321 | 1 | false | valid_template | 1/1/1 | true | true |
| 206 | 18 | 2 | case-007 | job-coverage, single-interest | valid_prose | output-205 | 93 | 313 | 1 | false | valid_template | 1/1/1 | true | true |
| 207 | 18 | 3 | case-008 | job-coverage, single-interest | valid_prose | output-206 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 208 | 18 | 4 | case-009 | job-coverage, single-interest | valid_prose | output-207 | 107 | 327 | 2 | false | valid_template | 1/1/1 | true | true |
| 209 | 18 | 5 | case-010 | job-coverage, single-interest | valid_prose | output-208 | 137 | 357 | 2 | false | valid_template | 1/1/1 | true | true |
| 210 | 18 | 6 | case-011 | job-coverage, single-interest | valid_prose | output-209 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 211 | 18 | 7 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-210 | 123 | 343 | 2 | false | valid_template | 1/1/1 | true | true |
| 212 | 18 | 8 | case-001 | job-coverage, single-interest | valid_prose | output-211 | 121 | 341 | 2 | false | valid_template | 1/1/1 | true | true |
| 213 | 18 | 9 | case-002 | job-coverage, single-interest | valid_prose | output-212 | 109 | 329 | 1 | false | valid_template | 1/1/1 | true | true |
| 214 | 18 | 10 | case-003 | job-coverage, single-interest | valid_prose | output-213 | 137 | 357 | 2 | false | valid_template | 1/1/1 | true | true |
| 215 | 18 | 11 | case-004 | job-coverage, multi-interest | valid_prose | output-214 | 113 | 333 | 2 | false | valid_template | 1/1/1 | true | true |
| 216 | 18 | 12 | case-005 | job-coverage, single-interest | valid_prose | output-215 | 131 | 351 | 2 | false | valid_template | 1/1/1 | true | true |
| 217 | 19 | 1 | case-007 | job-coverage, single-interest | valid_prose | output-216 | 102 | 322 | 1 | false | valid_template | 1/1/1 | true | true |
| 218 | 19 | 2 | case-008 | job-coverage, single-interest | valid_prose | output-217 | 98 | 318 | 1 | false | valid_template | 1/1/1 | true | true |
| 219 | 19 | 3 | case-009 | job-coverage, single-interest | valid_prose | output-218 | 162 | 382 | 2 | false | valid_template | 1/1/1 | true | true |
| 220 | 19 | 4 | case-010 | job-coverage, single-interest | valid_prose | output-219 | 90 | 310 | 1 | false | valid_template | 1/1/1 | true | true |
| 221 | 19 | 5 | case-011 | job-coverage, single-interest | valid_prose | output-220 | 177 | 397 | 2 | false | valid_template | 1/1/1 | true | true |
| 222 | 19 | 6 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-221 | 174 | 394 | 2 | false | valid_template | 1/1/1 | true | true |
| 223 | 19 | 7 | case-001 | job-coverage, single-interest | valid_prose | output-222 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 224 | 19 | 8 | case-002 | job-coverage, single-interest | valid_prose | output-223 | 91 | 311 | 1 | false | valid_template | 1/1/1 | true | true |
| 225 | 19 | 9 | case-003 | job-coverage, single-interest | valid_prose | output-224 | 136 | 356 | 2 | false | valid_template | 1/1/1 | true | true |
| 226 | 19 | 10 | case-004 | job-coverage, multi-interest | valid_prose | output-225 | 85 | 305 | 1 | false | valid_template | 1/1/1 | true | true |
| 227 | 19 | 11 | case-005 | job-coverage, single-interest | valid_prose | output-226 | 108 | 328 | 1 | false | valid_template | 1/1/1 | true | true |
| 228 | 19 | 12 | case-006 | job-coverage, single-interest | valid_prose | output-227 | 96 | 316 | 1 | false | valid_template | 1/1/1 | true | true |
| 229 | 20 | 1 | case-008 | job-coverage, single-interest | valid_prose | output-228 | 180 | 400 | 2 | false | valid_template | 1/1/1 | true | true |
| 230 | 20 | 2 | case-009 | job-coverage, single-interest | valid_prose | output-229 | 114 | 334 | 2 | false | valid_template | 1/1/1 | true | true |
| 231 | 20 | 3 | case-010 | job-coverage, single-interest | valid_prose | output-230 | 159 | 379 | 2 | false | valid_template | 1/1/1 | true | true |
| 232 | 20 | 4 | case-011 | job-coverage, single-interest | valid_prose | output-231 | 182 | 402 | 2 | false | valid_template | 1/1/1 | true | true |
| 233 | 20 | 5 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-232 | 133 | 353 | 1 | false | valid_template | 1/1/1 | true | true |
| 234 | 20 | 6 | case-001 | job-coverage, single-interest | valid_prose | output-233 | 144 | 364 | 2 | false | valid_template | 1/1/1 | true | true |
| 235 | 20 | 7 | case-002 | job-coverage, single-interest | valid_prose | output-234 | 132 | 352 | 2 | false | valid_template | 1/1/1 | true | true |
| 236 | 20 | 8 | case-003 | job-coverage, single-interest | valid_prose | output-235 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 237 | 20 | 9 | case-004 | job-coverage, multi-interest | valid_prose | output-236 | 107 | 327 | 1 | false | valid_template | 1/1/1 | true | true |
| 238 | 20 | 10 | case-005 | job-coverage, single-interest | valid_prose | output-237 | 114 | 334 | 1 | false | valid_template | 1/1/1 | true | true |
| 239 | 20 | 11 | case-006 | job-coverage, single-interest | valid_prose | output-238 | 88 | 308 | 1 | false | valid_template | 1/1/1 | true | true |
| 240 | 20 | 12 | case-007 | job-coverage, single-interest | valid_prose | output-239 | 164 | 384 | 2 | false | valid_template | 1/1/1 | true | true |
| 241 | 21 | 1 | case-009 | job-coverage, single-interest | valid_prose | output-240 | 149 | 369 | 2 | false | valid_template | 1/1/1 | true | true |
| 242 | 21 | 2 | case-010 | job-coverage, single-interest | valid_prose | output-241 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 243 | 21 | 3 | case-011 | job-coverage, single-interest | valid_prose | output-242 | 139 | 359 | 2 | false | valid_template | 1/1/1 | true | true |
| 244 | 21 | 4 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-243 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 245 | 21 | 5 | case-001 | job-coverage, single-interest | valid_prose | output-244 | 115 | 335 | 2 | false | valid_template | 1/1/1 | true | true |
| 246 | 21 | 6 | case-002 | job-coverage, single-interest | valid_prose | output-245 | 91 | 311 | 1 | false | valid_template | 1/1/1 | true | true |
| 247 | 21 | 7 | case-003 | job-coverage, single-interest | valid_prose | output-246 | 125 | 345 | 2 | false | valid_template | 1/1/1 | true | true |
| 248 | 21 | 8 | case-004 | job-coverage, multi-interest | valid_prose | output-247 | 122 | 342 | 2 | false | valid_template | 1/1/1 | true | true |
| 249 | 21 | 9 | case-005 | job-coverage, single-interest | valid_prose | output-248 | 116 | 336 | 1 | false | valid_template | 1/1/1 | true | true |
| 250 | 21 | 10 | case-006 | job-coverage, single-interest | valid_prose | output-249 | 104 | 324 | 1 | false | valid_template | 1/1/1 | true | true |
| 251 | 21 | 11 | case-007 | job-coverage, single-interest | valid_prose | output-250 | 110 | 330 | 2 | false | valid_template | 1/1/1 | true | true |
| 252 | 21 | 12 | case-008 | job-coverage, single-interest | valid_prose | output-251 | 147 | 367 | 2 | false | valid_template | 1/1/1 | true | true |
| 253 | 22 | 1 | case-010 | job-coverage, single-interest | valid_prose | output-252 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 254 | 22 | 2 | case-011 | job-coverage, single-interest | valid_prose | output-253 | 157 | 377 | 2 | false | valid_template | 1/1/1 | true | true |
| 255 | 22 | 3 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-254 | 76 | 296 | 1 | false | valid_template | 1/1/1 | true | true |
| 256 | 22 | 4 | case-001 | job-coverage, single-interest | valid_prose | output-255 | 124 | 344 | 2 | false | valid_template | 1/1/1 | true | true |
| 257 | 22 | 5 | case-002 | job-coverage, single-interest | valid_prose | output-256 | 108 | 328 | 1 | false | valid_template | 1/1/1 | true | true |
| 258 | 22 | 6 | case-003 | job-coverage, single-interest | valid_prose | output-257 | 118 | 338 | 2 | false | valid_template | 1/1/1 | true | true |
| 259 | 22 | 7 | case-004 | job-coverage, multi-interest | valid_prose | output-258 | 105 | 325 | 2 | false | valid_template | 1/1/1 | true | true |
| 260 | 22 | 8 | case-005 | job-coverage, single-interest | valid_prose | output-259 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 261 | 22 | 9 | case-006 | job-coverage, single-interest | valid_prose | output-260 | 133 | 353 | 2 | false | valid_template | 1/1/1 | true | true |
| 262 | 22 | 10 | case-007 | job-coverage, single-interest | valid_prose | output-261 | 147 | 367 | 2 | false | valid_template | 1/1/1 | true | true |
| 263 | 22 | 11 | case-008 | job-coverage, single-interest | valid_prose | output-262 | 171 | 391 | 2 | false | valid_template | 1/1/1 | true | true |
| 264 | 22 | 12 | case-009 | job-coverage, single-interest | valid_prose | output-263 | 126 | 346 | 2 | false | valid_template | 1/1/1 | true | true |
| 265 | 23 | 1 | case-011 | job-coverage, single-interest | valid_prose | output-264 | 176 | 396 | 2 | false | valid_template | 1/1/1 | true | true |
| 266 | 23 | 2 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-265 | 97 | 317 | 1 | false | valid_template | 1/1/1 | true | true |
| 267 | 23 | 3 | case-001 | job-coverage, single-interest | valid_prose | output-266 | 136 | 356 | 2 | false | valid_template | 1/1/1 | true | true |
| 268 | 23 | 4 | case-002 | job-coverage, single-interest | valid_prose | output-245 | 91 | 311 | 1 | false | valid_template | 1/1/1 | true | true |
| 269 | 23 | 5 | case-003 | job-coverage, single-interest | valid_prose | output-267 | 140 | 360 | 2 | false | valid_template | 1/1/1 | true | true |
| 270 | 23 | 6 | case-004 | job-coverage, multi-interest | valid_prose | output-268 | 112 | 332 | 2 | false | valid_template | 1/1/1 | true | true |
| 271 | 23 | 7 | case-005 | job-coverage, single-interest | valid_prose | output-269 | 96 | 316 | 1 | false | valid_template | 1/1/1 | true | true |
| 272 | 23 | 8 | case-006 | job-coverage, single-interest | valid_prose | output-270 | 87 | 307 | 1 | false | valid_template | 1/1/1 | true | true |
| 273 | 23 | 9 | case-007 | job-coverage, single-interest | valid_prose | output-271 | 119 | 339 | 2 | false | valid_template | 1/1/1 | true | true |
| 274 | 23 | 10 | case-008 | job-coverage, single-interest | valid_prose | output-272 | 79 | 299 | 1 | false | valid_template | 1/1/1 | true | true |
| 275 | 23 | 11 | case-009 | job-coverage, single-interest | valid_prose | output-273 | 117 | 337 | 2 | false | valid_template | 1/1/1 | true | true |
| 276 | 23 | 12 | case-010 | job-coverage, single-interest | valid_prose | output-274 | 173 | 393 | 2 | false | valid_template | 1/1/1 | true | true |
| 277 | 24 | 1 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-275 | 170 | 390 | 2 | false | valid_template | 1/1/1 | true | true |
| 278 | 24 | 2 | case-001 | job-coverage, single-interest | valid_prose | output-276 | 130 | 350 | 2 | false | valid_template | 1/1/1 | true | true |
| 279 | 24 | 3 | case-002 | job-coverage, single-interest | valid_prose | output-277 | 122 | 342 | 2 | false | valid_template | 1/1/1 | true | true |
| 280 | 24 | 4 | case-003 | job-coverage, single-interest | valid_prose | output-278 | 123 | 343 | 2 | false | valid_template | 1/1/1 | true | true |
| 281 | 24 | 5 | case-004 | job-coverage, multi-interest | valid_prose | output-279 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 282 | 24 | 6 | case-005 | job-coverage, single-interest | valid_prose | output-280 | 102 | 322 | 1 | false | valid_template | 1/1/1 | true | true |
| 283 | 24 | 7 | case-006 | job-coverage, single-interest | valid_prose | output-281 | 78 | 298 | 1 | false | valid_template | 1/1/1 | true | true |
| 284 | 24 | 8 | case-007 | job-coverage, single-interest | valid_prose | output-282 | 162 | 382 | 2 | false | valid_template | 1/1/1 | true | true |
| 285 | 24 | 9 | case-008 | job-coverage, single-interest | valid_prose | output-283 | 94 | 314 | 1 | false | valid_template | 1/1/1 | true | true |
| 286 | 24 | 10 | case-009 | job-coverage, single-interest | valid_prose | output-284 | 142 | 362 | 2 | false | valid_template | 1/1/1 | true | true |
| 287 | 24 | 11 | case-010 | job-coverage, single-interest | valid_prose | output-285 | 152 | 372 | 2 | false | valid_template | 1/1/1 | true | true |
| 288 | 24 | 12 | case-011 | job-coverage, single-interest | valid_prose | output-286 | 151 | 371 | 2 | false | valid_template | 1/1/1 | true | true |
| 289 | 25 | 1 | case-001 | job-coverage, single-interest | valid_prose | output-287 | 129 | 349 | 2 | false | valid_template | 1/1/1 | true | true |
| 290 | 25 | 2 | case-002 | job-coverage, single-interest | valid_prose | output-288 | 139 | 359 | 2 | false | valid_template | 1/1/1 | true | true |
| 291 | 25 | 3 | case-003 | job-coverage, single-interest | valid_prose | output-289 | 128 | 348 | 2 | false | valid_template | 1/1/1 | true | true |
| 292 | 25 | 4 | case-004 | job-coverage, multi-interest | valid_prose | output-290 | 98 | 318 | 1 | false | valid_template | 1/1/1 | true | true |
| 293 | 25 | 5 | case-005 | job-coverage, single-interest | valid_prose | output-291 | 113 | 333 | 1 | false | valid_template | 1/1/1 | true | true |
| 294 | 25 | 6 | case-006 | job-coverage, single-interest | valid_prose | output-292 | 168 | 388 | 2 | false | valid_template | 1/1/1 | true | true |
| 295 | 25 | 7 | case-007 | job-coverage, single-interest | valid_prose | output-293 | 131 | 351 | 2 | false | valid_template | 1/1/1 | true | true |
| 296 | 25 | 8 | case-008 | job-coverage, single-interest | valid_prose | output-294 | 118 | 338 | 2 | false | valid_template | 1/1/1 | true | true |
| 297 | 25 | 9 | case-009 | job-coverage, single-interest | valid_prose | output-295 | 134 | 354 | 2 | false | valid_template | 1/1/1 | true | true |
| 298 | 25 | 10 | case-010 | job-coverage, single-interest | valid_prose | output-296 | 127 | 347 | 2 | false | valid_template | 1/1/1 | true | true |
| 299 | 25 | 11 | case-011 | job-coverage, single-interest | valid_prose | output-297 | 143 | 363 | 2 | false | valid_template | 1/1/1 | true | true |
| 300 | 25 | 12 | case-012 | both-other, job-coverage, single-interest | valid_prose | output-298 | 168 | 388 | 2 | false | valid_template | 1/1/1 | true | true |

</details>

## Per-attempt transport and metering ledger

<details>
<summary>Show all 300 provider-attempt records</summary>

| Call | HTTP | Provider status | Diagnostic | Response bytes | Transport ms | Input | Output | Total | Cached | Reasoning | Tool | Output text cp | Refusals | Safety | Safe provider metadata |
|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 200 | completed | completed_structured_output | 3826 | 2684 | 251 | 51 | 302 | 0 | 0 | — | 179 | 0 | 0 | openai-processing-ms=1380; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 2 | 200 | completed | completed_structured_output | 3798 | 1246 | 250 | 44 | 294 | 0 | 0 | — | 151 | 0 | 0 | openai-processing-ms=990; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 3 | 200 | completed | completed_structured_output | 3827 | 1929 | 251 | 50 | 301 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1589; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 4 | 200 | completed | completed_structured_output | 3838 | 1310 | 252 | 55 | 307 | 0 | 0 | — | 191 | 0 | 0 | openai-processing-ms=1034; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 5 | 200 | completed | completed_structured_output | 3841 | 1668 | 250 | 54 | 304 | 0 | 0 | — | 194 | 0 | 0 | openai-processing-ms=1314; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 6 | 200 | completed | completed_structured_output | 3785 | 1434 | 251 | 42 | 293 | 0 | 0 | — | 138 | 0 | 0 | openai-processing-ms=1148; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 7 | 200 | completed | completed_structured_output | 3760 | 2092 | 248 | 40 | 288 | 0 | 0 | — | 113 | 0 | 0 | openai-processing-ms=1841; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 8 | 200 | completed | completed_structured_output | 3809 | 1624 | 252 | 47 | 299 | 0 | 0 | — | 162 | 0 | 0 | openai-processing-ms=1356; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 9 | 200 | completed | completed_structured_output | 3815 | 1116 | 252 | 47 | 299 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=886; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 10 | 200 | completed | completed_structured_output | 3861 | 1469 | 253 | 56 | 309 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1230; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 11 | 200 | completed | completed_structured_output | 3822 | 3826 | 249 | 47 | 296 | 0 | 0 | — | 175 | 0 | 0 | openai-processing-ms=1955; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 12 | 200 | completed | completed_structured_output | 3827 | 1243 | 247 | 50 | 297 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1025; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 13 | 200 | completed | completed_structured_output | 3802 | 1326 | 250 | 46 | 296 | 0 | 0 | — | 155 | 0 | 0 | openai-processing-ms=1112; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 14 | 200 | completed | completed_structured_output | 3821 | 1378 | 251 | 48 | 299 | 0 | 0 | — | 174 | 0 | 0 | openai-processing-ms=1166; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 15 | 200 | completed | completed_structured_output | 3814 | 1199 | 252 | 47 | 299 | 0 | 0 | — | 167 | 0 | 0 | openai-processing-ms=976; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 16 | 200 | completed | completed_structured_output | 3832 | 1461 | 250 | 52 | 302 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=1225; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 17 | 200 | completed | completed_structured_output | 3800 | 1557 | 251 | 50 | 301 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=1340; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 18 | 200 | completed | completed_structured_output | 3786 | 1084 | 248 | 43 | 291 | 0 | 0 | — | 139 | 0 | 0 | openai-processing-ms=864; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 19 | 200 | completed | completed_structured_output | 3820 | 1476 | 252 | 51 | 303 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1172; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 20 | 200 | completed | completed_structured_output | 3797 | 1103 | 252 | 45 | 297 | 0 | 0 | — | 150 | 0 | 0 | openai-processing-ms=889; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 21 | 200 | completed | completed_structured_output | 3861 | 1491 | 253 | 58 | 311 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1184; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 22 | 200 | completed | completed_structured_output | 3858 | 1317 | 249 | 55 | 304 | 0 | 0 | — | 211 | 0 | 0 | openai-processing-ms=1085; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 23 | 200 | completed | completed_structured_output | 3777 | 1453 | 247 | 40 | 287 | 0 | 0 | — | 130 | 0 | 0 | openai-processing-ms=1236; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 24 | 200 | completed | completed_structured_output | 3819 | 1310 | 251 | 51 | 302 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1096; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 25 | 200 | completed | completed_structured_output | 3825 | 1338 | 251 | 50 | 301 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=1050; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 26 | 200 | completed | completed_structured_output | 3808 | 3004 | 252 | 45 | 297 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=1497; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 27 | 200 | completed | completed_structured_output | 3825 | 1515 | 250 | 53 | 303 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=1267; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 28 | 200 | completed | completed_structured_output | 3766 | 1239 | 251 | 39 | 290 | 0 | 0 | — | 119 | 0 | 0 | openai-processing-ms=1029; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 29 | 200 | completed | completed_structured_output | 3835 | 1373 | 248 | 50 | 298 | 0 | 0 | — | 188 | 0 | 0 | openai-processing-ms=1142; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 30 | 200 | completed | completed_structured_output | 3772 | 1263 | 252 | 40 | 292 | 0 | 0 | — | 125 | 0 | 0 | openai-processing-ms=1043; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 31 | 200 | completed | completed_structured_output | 3805 | 1352 | 252 | 49 | 301 | 0 | 0 | — | 158 | 0 | 0 | openai-processing-ms=1121; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 32 | 200 | completed | completed_structured_output | 3785 | 1294 | 253 | 41 | 294 | 0 | 0 | — | 138 | 0 | 0 | openai-processing-ms=1061; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 33 | 200 | completed | completed_structured_output | 3774 | 1041 | 249 | 40 | 289 | 0 | 0 | — | 127 | 0 | 0 | openai-processing-ms=819; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 34 | 200 | completed | completed_structured_output | 3800 | 1282 | 247 | 42 | 289 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=1020; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 35 | 200 | completed | completed_structured_output | 3816 | 1292 | 251 | 49 | 300 | 0 | 0 | — | 169 | 0 | 0 | openai-processing-ms=1015; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 36 | 200 | completed | completed_structured_output | 3845 | 1367 | 250 | 58 | 308 | 0 | 0 | — | 198 | 0 | 0 | openai-processing-ms=1138; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 37 | 200 | completed | completed_structured_output | 3820 | 1352 | 252 | 47 | 299 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1119; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 38 | 200 | completed | completed_structured_output | 3823 | 1277 | 250 | 50 | 300 | 0 | 0 | — | 176 | 0 | 0 | openai-processing-ms=1027; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 39 | 200 | completed | completed_structured_output | 3834 | 1476 | 251 | 50 | 301 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1232; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 40 | 200 | completed | completed_structured_output | 3808 | 1451 | 248 | 48 | 296 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=1120; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 41 | 200 | completed | completed_structured_output | 3847 | 1489 | 252 | 56 | 308 | 0 | 0 | — | 200 | 0 | 0 | openai-processing-ms=1265; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 42 | 200 | completed | completed_structured_output | 3796 | 1322 | 252 | 47 | 299 | 0 | 0 | — | 149 | 0 | 0 | openai-processing-ms=1054; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 43 | 200 | completed | completed_structured_output | 3831 | 1321 | 253 | 51 | 304 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=1082; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 44 | 200 | completed | completed_structured_output | 3820 | 1254 | 249 | 49 | 298 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1019; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 45 | 200 | completed | completed_structured_output | 3768 | 1097 | 247 | 38 | 285 | 0 | 0 | — | 121 | 0 | 0 | openai-processing-ms=880; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 46 | 200 | completed | completed_structured_output | 3810 | 1302 | 251 | 47 | 298 | 0 | 0 | — | 163 | 0 | 0 | openai-processing-ms=1069; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 47 | 200 | completed | completed_structured_output | 3809 | 1689 | 250 | 49 | 299 | 0 | 0 | — | 162 | 0 | 0 | openai-processing-ms=1389; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 48 | 200 | completed | completed_structured_output | 3815 | 1410 | 251 | 46 | 297 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=1175; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 49 | 200 | completed | completed_structured_output | 3800 | 3809 | 250 | 44 | 294 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=3515; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 50 | 200 | completed | completed_structured_output | 3769 | 1376 | 251 | 40 | 291 | 0 | 0 | — | 122 | 0 | 0 | openai-processing-ms=1057; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 51 | 200 | completed | completed_structured_output | 3836 | 1226 | 248 | 49 | 297 | 0 | 0 | — | 189 | 0 | 0 | openai-processing-ms=1013; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 52 | 200 | completed | completed_structured_output | 3845 | 1565 | 252 | 55 | 307 | 0 | 0 | — | 198 | 0 | 0 | openai-processing-ms=1260; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 53 | 200 | completed | completed_structured_output | 3837 | 1853 | 252 | 56 | 308 | 0 | 0 | — | 190 | 0 | 0 | openai-processing-ms=1607; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 54 | 200 | completed | completed_structured_output | 3875 | 1363 | 253 | 64 | 317 | 0 | 0 | — | 228 | 0 | 0 | openai-processing-ms=1047; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 55 | 200 | completed | completed_structured_output | 3815 | 1280 | 249 | 45 | 294 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=1066; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 56 | 200 | completed | completed_structured_output | 3862 | 1482 | 247 | 54 | 301 | 0 | 0 | — | 215 | 0 | 0 | openai-processing-ms=1277; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 57 | 200 | completed | completed_structured_output | 3821 | 1328 | 251 | 51 | 302 | 0 | 0 | — | 174 | 0 | 0 | openai-processing-ms=1124; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 58 | 200 | completed | completed_structured_output | 3841 | 1194 | 250 | 53 | 303 | 0 | 0 | — | 194 | 0 | 0 | openai-processing-ms=887; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 59 | 200 | completed | completed_structured_output | 3812 | 1958 | 251 | 49 | 300 | 0 | 0 | — | 165 | 0 | 0 | openai-processing-ms=1743; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 60 | 200 | completed | completed_structured_output | 3777 | 1321 | 252 | 43 | 295 | 0 | 0 | — | 130 | 0 | 0 | openai-processing-ms=1088; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 61 | 200 | completed | completed_structured_output | 3789 | 1832 | 251 | 42 | 293 | 0 | 0 | — | 142 | 0 | 0 | openai-processing-ms=1599; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 62 | 200 | completed | completed_structured_output | 3797 | 1255 | 248 | 45 | 293 | 0 | 0 | — | 150 | 0 | 0 | openai-processing-ms=1022; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 63 | 200 | completed | completed_structured_output | 3831 | 1559 | 252 | 52 | 304 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=1353; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 64 | 200 | completed | completed_structured_output | 3818 | 1315 | 252 | 46 | 298 | 0 | 0 | — | 171 | 0 | 0 | openai-processing-ms=1107; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 65 | 200 | completed | completed_structured_output | 3835 | 1329 | 253 | 51 | 304 | 0 | 0 | — | 188 | 0 | 0 | openai-processing-ms=1108; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 66 | 200 | completed | completed_structured_output | 3846 | 1412 | 249 | 53 | 302 | 0 | 0 | — | 199 | 0 | 0 | openai-processing-ms=1179; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 67 | 200 | completed | completed_structured_output | 3849 | 1194 | 247 | 50 | 297 | 0 | 0 | — | 202 | 0 | 0 | openai-processing-ms=974; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 68 | 200 | completed | completed_structured_output | 3842 | 1258 | 251 | 56 | 307 | 0 | 0 | — | 195 | 0 | 0 | openai-processing-ms=1007; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 69 | 200 | completed | completed_structured_output | 3779 | 1368 | 250 | 43 | 293 | 0 | 0 | — | 132 | 0 | 0 | openai-processing-ms=1164; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 70 | 200 | completed | completed_structured_output | 3834 | 1619 | 251 | 52 | 303 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1169; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 71 | 200 | completed | completed_structured_output | 3810 | 2640 | 252 | 47 | 299 | 0 | 0 | — | 163 | 0 | 0 | openai-processing-ms=2047; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 72 | 200 | completed | completed_structured_output | 3799 | 1454 | 250 | 44 | 294 | 0 | 0 | — | 152 | 0 | 0 | openai-processing-ms=1221; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 73 | 200 | completed | completed_structured_output | 3833 | 1475 | 248 | 51 | 299 | 0 | 0 | — | 186 | 0 | 0 | openai-processing-ms=1259; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 74 | 200 | completed | completed_structured_output | 3845 | 1347 | 252 | 54 | 306 | 0 | 0 | — | 198 | 0 | 0 | openai-processing-ms=1058; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 75 | 200 | completed | completed_structured_output | 3803 | 1215 | 252 | 48 | 300 | 0 | 0 | — | 156 | 0 | 0 | openai-processing-ms=970; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 76 | 200 | completed | completed_structured_output | 3845 | 1425 | 253 | 51 | 304 | 0 | 0 | — | 198 | 0 | 0 | openai-processing-ms=1078; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 77 | 200 | completed | completed_structured_output | 3834 | 1267 | 249 | 51 | 300 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1029; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 78 | 200 | completed | completed_structured_output | 3780 | 1174 | 247 | 39 | 286 | 0 | 0 | — | 133 | 0 | 0 | openai-processing-ms=926; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 79 | 200 | completed | completed_structured_output | 3831 | 1247 | 251 | 51 | 302 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=1011; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 80 | 200 | completed | completed_structured_output | 3817 | 2671 | 250 | 49 | 299 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=2325; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 81 | 200 | completed | completed_structured_output | 3823 | 1485 | 251 | 47 | 298 | 0 | 0 | — | 176 | 0 | 0 | openai-processing-ms=1060; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 82 | 200 | completed | completed_structured_output | 3812 | 1211 | 252 | 47 | 299 | 0 | 0 | — | 165 | 0 | 0 | openai-processing-ms=999; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 83 | 200 | completed | completed_structured_output | 3778 | 1152 | 250 | 37 | 287 | 0 | 0 | — | 131 | 0 | 0 | openai-processing-ms=888; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 84 | 200 | completed | completed_structured_output | 3831 | 1287 | 251 | 50 | 301 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=1045; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 85 | 200 | completed | completed_structured_output | 3775 | 1191 | 252 | 43 | 295 | 0 | 0 | — | 128 | 0 | 0 | openai-processing-ms=970; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 86 | 200 | completed | completed_structured_output | 3817 | 1244 | 252 | 51 | 303 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=994; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 87 | 200 | completed | completed_structured_output | 3838 | 1313 | 253 | 51 | 304 | 0 | 0 | — | 191 | 0 | 0 | openai-processing-ms=1022; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 88 | 200 | completed | completed_structured_output | 3790 | 1496 | 249 | 41 | 290 | 0 | 0 | — | 143 | 0 | 0 | openai-processing-ms=1285; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 89 | 200 | completed | completed_structured_output | 3845 | 1166 | 247 | 53 | 300 | 0 | 0 | — | 198 | 0 | 0 | openai-processing-ms=956; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 90 | 200 | completed | completed_structured_output | 3861 | 1314 | 251 | 57 | 308 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1111; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 91 | 200 | completed | completed_structured_output | 3795 | 1085 | 250 | 44 | 294 | 0 | 0 | — | 148 | 0 | 0 | openai-processing-ms=850; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 92 | 200 | completed | completed_structured_output | 3819 | 1254 | 251 | 48 | 299 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1021; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 93 | 200 | completed | completed_structured_output | 3834 | 1306 | 252 | 56 | 308 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1052; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 94 | 200 | completed | completed_structured_output | 3822 | 1351 | 250 | 52 | 302 | 0 | 0 | — | 175 | 0 | 0 | openai-processing-ms=1133; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 95 | 200 | completed | completed_structured_output | 3804 | 1081 | 251 | 44 | 295 | 0 | 0 | — | 157 | 0 | 0 | openai-processing-ms=873; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 96 | 200 | completed | completed_structured_output | 3817 | 1261 | 248 | 48 | 296 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=1059; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 97 | 200 | completed | completed_structured_output | 3755 | 3093 | 252 | 36 | 288 | 0 | 0 | — | 108 | 0 | 0 | openai-processing-ms=2835; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 98 | 200 | completed | completed_structured_output | 3795 | 1207 | 253 | 45 | 298 | 0 | 0 | — | 148 | 0 | 0 | openai-processing-ms=994; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 99 | 200 | completed | completed_structured_output | 3828 | 1811 | 249 | 51 | 300 | 0 | 0 | — | 181 | 0 | 0 | openai-processing-ms=1553; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 100 | 200 | completed | completed_structured_output | 3839 | 2707 | 247 | 49 | 296 | 0 | 0 | — | 192 | 0 | 0 | openai-processing-ms=2299; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 101 | 200 | completed | completed_structured_output | 3818 | 1235 | 251 | 48 | 299 | 0 | 0 | — | 171 | 0 | 0 | openai-processing-ms=939; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 102 | 200 | completed | completed_structured_output | 3791 | 1215 | 250 | 43 | 293 | 0 | 0 | — | 144 | 0 | 0 | openai-processing-ms=1010; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 103 | 200 | completed | completed_structured_output | 3826 | 1240 | 251 | 47 | 298 | 0 | 0 | — | 179 | 0 | 0 | openai-processing-ms=1010; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 104 | 200 | completed | completed_structured_output | 3819 | 1240 | 252 | 47 | 299 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1016; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 105 | 200 | completed | completed_structured_output | 3801 | 1198 | 250 | 46 | 296 | 0 | 0 | — | 154 | 0 | 0 | openai-processing-ms=974; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 106 | 200 | completed | completed_structured_output | 3863 | 1255 | 251 | 56 | 307 | 0 | 0 | — | 216 | 0 | 0 | openai-processing-ms=1022; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 107 | 200 | completed | completed_structured_output | 3772 | 1241 | 248 | 37 | 285 | 0 | 0 | — | 125 | 0 | 0 | openai-processing-ms=976; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 108 | 200 | completed | completed_structured_output | 3784 | 1294 | 252 | 42 | 294 | 0 | 0 | — | 137 | 0 | 0 | openai-processing-ms=1087; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 109 | 200 | completed | completed_structured_output | 3829 | 1245 | 253 | 51 | 304 | 0 | 0 | — | 182 | 0 | 0 | openai-processing-ms=1005; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 110 | 200 | completed | completed_structured_output | 3793 | 1309 | 249 | 41 | 290 | 0 | 0 | — | 146 | 0 | 0 | openai-processing-ms=1006; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 111 | 200 | completed | completed_structured_output | 3836 | 2399 | 247 | 50 | 297 | 0 | 0 | — | 189 | 0 | 0 | openai-processing-ms=2152; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 112 | 200 | completed | completed_structured_output | 3802 | 1303 | 251 | 47 | 298 | 0 | 0 | — | 155 | 0 | 0 | openai-processing-ms=1096; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 113 | 200 | completed | completed_structured_output | 3832 | 1177 | 250 | 50 | 300 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=967; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 114 | 200 | completed | completed_structured_output | 3815 | 1248 | 251 | 50 | 301 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=1020; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 115 | 200 | completed | completed_structured_output | 3782 | 1153 | 252 | 42 | 294 | 0 | 0 | — | 135 | 0 | 0 | openai-processing-ms=914; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 116 | 200 | completed | completed_structured_output | 3800 | 1415 | 250 | 46 | 296 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=1184; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 117 | 200 | completed | completed_structured_output | 3762 | 3073 | 251 | 40 | 291 | 0 | 0 | — | 115 | 0 | 0 | openai-processing-ms=2868; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 118 | 200 | completed | completed_structured_output | 3833 | 1276 | 248 | 50 | 298 | 0 | 0 | — | 186 | 0 | 0 | openai-processing-ms=958; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 119 | 200 | completed | completed_structured_output | 3862 | 1357 | 252 | 58 | 310 | 0 | 0 | — | 215 | 0 | 0 | openai-processing-ms=1139; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 120 | 200 | completed | completed_structured_output | 3802 | 1417 | 252 | 45 | 297 | 0 | 0 | — | 155 | 0 | 0 | openai-processing-ms=1212; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 121 | 200 | completed | completed_structured_output | 3855 | 1269 | 249 | 53 | 302 | 0 | 0 | — | 208 | 0 | 0 | openai-processing-ms=1037; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 122 | 200 | completed | completed_structured_output | 3813 | 1213 | 247 | 47 | 294 | 0 | 0 | — | 166 | 0 | 0 | openai-processing-ms=909; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 123 | 200 | completed | completed_structured_output | 3824 | 3485 | 251 | 49 | 300 | 0 | 0 | — | 177 | 0 | 0 | openai-processing-ms=3229; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 124 | 200 | completed | completed_structured_output | 3770 | 1300 | 250 | 41 | 291 | 0 | 0 | — | 123 | 0 | 0 | openai-processing-ms=952; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 125 | 200 | completed | completed_structured_output | 3830 | 1404 | 251 | 51 | 302 | 0 | 0 | — | 183 | 0 | 0 | openai-processing-ms=1104; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 126 | 200 | completed | completed_structured_output | 3827 | 1451 | 252 | 46 | 298 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1212; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 127 | 200 | completed | completed_structured_output | 3805 | 6228 | 250 | 47 | 297 | 0 | 0 | — | 158 | 0 | 0 | openai-processing-ms=5672; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 128 | 200 | completed | completed_structured_output | 3794 | 1199 | 251 | 42 | 293 | 0 | 0 | — | 147 | 0 | 0 | openai-processing-ms=953; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 129 | 200 | completed | completed_structured_output | 3832 | 1293 | 248 | 52 | 300 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=1058; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 130 | 200 | completed | completed_structured_output | 3770 | 1295 | 252 | 40 | 292 | 0 | 0 | — | 123 | 0 | 0 | openai-processing-ms=1011; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 131 | 200 | completed | completed_structured_output | 3806 | 1364 | 252 | 45 | 297 | 0 | 0 | — | 159 | 0 | 0 | openai-processing-ms=1160; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 132 | 200 | completed | completed_structured_output | 3857 | 3115 | 253 | 57 | 310 | 0 | 0 | — | 210 | 0 | 0 | openai-processing-ms=2763; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 133 | 200 | completed | completed_structured_output | 3801 | 1282 | 247 | 43 | 290 | 0 | 0 | — | 154 | 0 | 0 | openai-processing-ms=1066; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 134 | 200 | completed | completed_structured_output | 3800 | 1280 | 251 | 48 | 299 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=1064; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 135 | 200 | completed | completed_structured_output | 3858 | 1514 | 250 | 54 | 304 | 0 | 0 | — | 211 | 0 | 0 | openai-processing-ms=1238; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 136 | 200 | completed | completed_structured_output | 3835 | 1413 | 251 | 54 | 305 | 0 | 0 | — | 188 | 0 | 0 | openai-processing-ms=1211; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 137 | 200 | completed | completed_structured_output | 3810 | 1706 | 252 | 45 | 297 | 0 | 0 | — | 163 | 0 | 0 | openai-processing-ms=1437; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 138 | 200 | completed | completed_structured_output | 3825 | 1348 | 250 | 52 | 302 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=1040; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 139 | 200 | completed | completed_structured_output | 3819 | 1383 | 251 | 47 | 298 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1184; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 140 | 200 | completed | completed_structured_output | 3833 | 1488 | 248 | 51 | 299 | 0 | 0 | — | 186 | 0 | 0 | openai-processing-ms=1218; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 141 | 200 | completed | completed_structured_output | 3855 | 1349 | 252 | 56 | 308 | 0 | 0 | — | 208 | 0 | 0 | openai-processing-ms=1131; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 142 | 200 | completed | completed_structured_output | 3762 | 3422 | 252 | 37 | 289 | 0 | 0 | — | 115 | 0 | 0 | openai-processing-ms=3187; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 143 | 200 | completed | completed_structured_output | 3854 | 1283 | 253 | 53 | 306 | 0 | 0 | — | 207 | 0 | 0 | openai-processing-ms=1057; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 144 | 200 | completed | completed_structured_output | 3844 | 1305 | 249 | 53 | 302 | 0 | 0 | — | 197 | 0 | 0 | openai-processing-ms=1063; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 145 | 200 | completed | completed_structured_output | 3811 | 1362 | 251 | 48 | 299 | 0 | 0 | — | 164 | 0 | 0 | openai-processing-ms=1153; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 146 | 200 | completed | completed_structured_output | 3847 | 1256 | 250 | 55 | 305 | 0 | 0 | — | 200 | 0 | 0 | openai-processing-ms=1042; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 147 | 200 | completed | completed_structured_output | 3844 | 1474 | 251 | 54 | 305 | 0 | 0 | — | 197 | 0 | 0 | openai-processing-ms=1266; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 148 | 200 | completed | completed_structured_output | 3779 | 1389 | 252 | 43 | 295 | 0 | 0 | — | 132 | 0 | 0 | openai-processing-ms=1147; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 149 | 200 | completed | completed_structured_output | 3820 | 1227 | 250 | 49 | 299 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1017; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 150 | 200 | completed | completed_structured_output | 3791 | 1360 | 251 | 43 | 294 | 0 | 0 | — | 144 | 0 | 0 | openai-processing-ms=1135; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 151 | 200 | completed | completed_structured_output | 3861 | 1244 | 248 | 58 | 306 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1015; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 152 | 200 | completed | completed_structured_output | 3769 | 1338 | 252 | 39 | 291 | 0 | 0 | — | 122 | 0 | 0 | openai-processing-ms=1135; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 153 | 200 | completed | completed_structured_output | 3817 | 1262 | 252 | 50 | 302 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=1034; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 154 | 200 | completed | completed_structured_output | 3856 | 1346 | 253 | 57 | 310 | 0 | 0 | — | 209 | 0 | 0 | openai-processing-ms=1118; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 155 | 200 | completed | completed_structured_output | 3831 | 1313 | 249 | 51 | 300 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=1050; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 156 | 200 | completed | completed_structured_output | 3818 | 1464 | 247 | 47 | 294 | 0 | 0 | — | 171 | 0 | 0 | openai-processing-ms=1227; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 157 | 200 | completed | completed_structured_output | 3833 | 1403 | 250 | 54 | 304 | 0 | 0 | — | 186 | 0 | 0 | openai-processing-ms=1188; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 158 | 200 | completed | completed_structured_output | 3828 | 1204 | 251 | 52 | 303 | 0 | 0 | — | 181 | 0 | 0 | openai-processing-ms=999; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 159 | 200 | completed | completed_structured_output | 3808 | 1189 | 252 | 45 | 297 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=958; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 160 | 200 | completed | completed_structured_output | 3801 | 1355 | 250 | 45 | 295 | 0 | 0 | — | 154 | 0 | 0 | openai-processing-ms=1121; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 161 | 200 | completed | completed_structured_output | 3825 | 1163 | 251 | 52 | 303 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=951; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 162 | 200 | completed | completed_structured_output | 3834 | 2115 | 248 | 50 | 298 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1851; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 163 | 200 | completed | completed_structured_output | 3847 | 3297 | 252 | 56 | 308 | 0 | 0 | — | 200 | 0 | 0 | openai-processing-ms=2985; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 164 | 200 | completed | completed_structured_output | 3809 | 1320 | 252 | 48 | 300 | 0 | 0 | — | 162 | 0 | 0 | openai-processing-ms=1082; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 165 | 200 | completed | completed_structured_output | 3886 | 1432 | 253 | 61 | 314 | 0 | 0 | — | 239 | 0 | 0 | openai-processing-ms=1124; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 166 | 200 | completed | completed_structured_output | 3828 | 2039 | 249 | 51 | 300 | 0 | 0 | — | 181 | 0 | 0 | openai-processing-ms=1795; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 167 | 200 | completed | completed_structured_output | 3837 | 1654 | 247 | 50 | 297 | 0 | 0 | — | 190 | 0 | 0 | openai-processing-ms=1454; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 168 | 200 | completed | completed_structured_output | 3861 | 3352 | 251 | 58 | 309 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=3070; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 169 | 200 | completed | completed_structured_output | 3841 | 3463 | 251 | 54 | 305 | 0 | 0 | — | 194 | 0 | 0 | openai-processing-ms=3213; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 170 | 200 | completed | completed_structured_output | 3847 | 1398 | 252 | 54 | 306 | 0 | 0 | — | 200 | 0 | 0 | openai-processing-ms=1161; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 171 | 200 | completed | completed_structured_output | 3811 | 1261 | 250 | 49 | 299 | 0 | 0 | — | 164 | 0 | 0 | openai-processing-ms=1057; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 172 | 200 | completed | completed_structured_output | 3827 | 1821 | 251 | 50 | 301 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1526; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 173 | 200 | completed | completed_structured_output | 3836 | 1425 | 248 | 51 | 299 | 0 | 0 | — | 189 | 0 | 0 | openai-processing-ms=1137; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 174 | 200 | completed | completed_structured_output | 3759 | 1818 | 252 | 39 | 291 | 0 | 0 | — | 112 | 0 | 0 | openai-processing-ms=1569; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 175 | 200 | completed | completed_structured_output | 3813 | 1328 | 252 | 50 | 302 | 0 | 0 | — | 166 | 0 | 0 | openai-processing-ms=1105; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 176 | 200 | completed | completed_structured_output | 3867 | 1535 | 253 | 60 | 313 | 0 | 0 | — | 220 | 0 | 0 | openai-processing-ms=1305; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 177 | 200 | completed | completed_structured_output | 3824 | 1317 | 249 | 47 | 296 | 0 | 0 | — | 177 | 0 | 0 | openai-processing-ms=1088; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 178 | 200 | completed | completed_structured_output | 3872 | 1386 | 247 | 55 | 302 | 0 | 0 | — | 225 | 0 | 0 | openai-processing-ms=1148; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 179 | 200 | completed | completed_structured_output | 3825 | 1244 | 251 | 49 | 300 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=1012; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 180 | 200 | completed | completed_structured_output | 3839 | 1549 | 250 | 55 | 305 | 0 | 0 | — | 192 | 0 | 0 | openai-processing-ms=1327; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 181 | 200 | completed | completed_structured_output | 3756 | 1085 | 252 | 41 | 293 | 0 | 0 | — | 109 | 0 | 0 | openai-processing-ms=873; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 182 | 200 | completed | completed_structured_output | 3812 | 1199 | 250 | 48 | 298 | 0 | 0 | — | 165 | 0 | 0 | openai-processing-ms=979; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 183 | 200 | completed | completed_structured_output | 3825 | 1558 | 251 | 52 | 303 | 0 | 0 | — | 178 | 0 | 0 | openai-processing-ms=1268; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 184 | 200 | completed | completed_structured_output | 3823 | 1328 | 248 | 47 | 295 | 0 | 0 | — | 176 | 0 | 0 | openai-processing-ms=1122; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 185 | 200 | completed | completed_structured_output | 3848 | 1606 | 252 | 57 | 309 | 0 | 0 | — | 201 | 0 | 0 | openai-processing-ms=1391; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 186 | 200 | completed | completed_structured_output | 3846 | 1454 | 252 | 55 | 307 | 0 | 0 | — | 199 | 0 | 0 | openai-processing-ms=1215; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 187 | 200 | completed | completed_structured_output | 3858 | 1418 | 253 | 53 | 306 | 0 | 0 | — | 211 | 0 | 0 | openai-processing-ms=1216; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 188 | 200 | completed | completed_structured_output | 3840 | 1301 | 249 | 53 | 302 | 0 | 0 | — | 193 | 0 | 0 | openai-processing-ms=1086; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 189 | 200 | completed | completed_structured_output | 3832 | 1326 | 247 | 50 | 297 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=1093; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 190 | 200 | completed | completed_structured_output | 3829 | 1211 | 251 | 51 | 302 | 0 | 0 | — | 182 | 0 | 0 | openai-processing-ms=973; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 191 | 200 | completed | completed_structured_output | 3842 | 1288 | 250 | 55 | 305 | 0 | 0 | — | 195 | 0 | 0 | openai-processing-ms=1084; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 192 | 200 | completed | completed_structured_output | 3836 | 1259 | 251 | 51 | 302 | 0 | 0 | — | 189 | 0 | 0 | openai-processing-ms=1019; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 193 | 200 | completed | completed_structured_output | 3749 | 1180 | 250 | 37 | 287 | 0 | 0 | — | 102 | 0 | 0 | openai-processing-ms=942; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 194 | 200 | completed | completed_structured_output | 3796 | 1167 | 251 | 43 | 294 | 0 | 0 | — | 149 | 0 | 0 | openai-processing-ms=922; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 195 | 200 | completed | completed_structured_output | 3839 | 1412 | 248 | 51 | 299 | 0 | 0 | — | 192 | 0 | 0 | openai-processing-ms=1170; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 196 | 200 | completed | completed_structured_output | 3857 | 1380 | 252 | 61 | 313 | 0 | 0 | — | 210 | 0 | 0 | openai-processing-ms=1164; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 197 | 200 | completed | completed_structured_output | 3829 | 1078 | 252 | 53 | 305 | 0 | 0 | — | 182 | 0 | 0 | openai-processing-ms=858; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 198 | 200 | completed | completed_structured_output | 3815 | 1391 | 253 | 47 | 300 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=1159; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 199 | 200 | completed | completed_structured_output | 3807 | 1356 | 249 | 49 | 298 | 0 | 0 | — | 160 | 0 | 0 | openai-processing-ms=1147; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 200 | 200 | completed | completed_structured_output | 3839 | 1411 | 247 | 51 | 298 | 0 | 0 | — | 192 | 0 | 0 | openai-processing-ms=1151; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 201 | 200 | completed | completed_structured_output | 3831 | 1179 | 251 | 53 | 304 | 0 | 0 | — | 184 | 0 | 0 | openai-processing-ms=973; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 202 | 200 | completed | completed_structured_output | 3808 | 1353 | 250 | 47 | 297 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=1100; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 203 | 200 | completed | completed_structured_output | 3822 | 1390 | 251 | 49 | 300 | 0 | 0 | — | 175 | 0 | 0 | openai-processing-ms=1181; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 204 | 200 | completed | completed_structured_output | 3836 | 1137 | 252 | 51 | 303 | 0 | 0 | — | 189 | 0 | 0 | openai-processing-ms=932; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 205 | 200 | completed | completed_structured_output | 3791 | 1087 | 251 | 43 | 294 | 0 | 0 | — | 144 | 0 | 0 | openai-processing-ms=850; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 206 | 200 | completed | completed_structured_output | 3783 | 1089 | 248 | 43 | 291 | 0 | 0 | — | 136 | 0 | 0 | openai-processing-ms=866; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 207 | 200 | completed | completed_structured_output | 3832 | 1467 | 252 | 52 | 304 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=1220; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 208 | 200 | completed | completed_structured_output | 3797 | 1256 | 252 | 47 | 299 | 0 | 0 | — | 150 | 0 | 0 | openai-processing-ms=1012; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 209 | 200 | completed | completed_structured_output | 3827 | 1308 | 253 | 50 | 303 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1092; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 210 | 200 | completed | completed_structured_output | 3819 | 1247 | 249 | 49 | 298 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1031; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 211 | 200 | completed | completed_structured_output | 3813 | 1305 | 247 | 47 | 294 | 0 | 0 | — | 166 | 0 | 0 | openai-processing-ms=1082; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 212 | 200 | completed | completed_structured_output | 3811 | 1379 | 251 | 48 | 299 | 0 | 0 | — | 164 | 0 | 0 | openai-processing-ms=1148; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 213 | 200 | completed | completed_structured_output | 3799 | 1530 | 250 | 46 | 296 | 0 | 0 | — | 152 | 0 | 0 | openai-processing-ms=1200; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 214 | 200 | completed | completed_structured_output | 3827 | 1242 | 251 | 50 | 301 | 0 | 0 | — | 180 | 0 | 0 | openai-processing-ms=1019; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 215 | 200 | completed | completed_structured_output | 3803 | 1295 | 252 | 44 | 296 | 0 | 0 | — | 156 | 0 | 0 | openai-processing-ms=1064; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 216 | 200 | completed | completed_structured_output | 3821 | 1317 | 250 | 52 | 302 | 0 | 0 | — | 174 | 0 | 0 | openai-processing-ms=1070; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 217 | 200 | completed | completed_structured_output | 3792 | 1314 | 248 | 44 | 292 | 0 | 0 | — | 145 | 0 | 0 | openai-processing-ms=1009; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 218 | 200 | completed | completed_structured_output | 3788 | 1299 | 252 | 43 | 295 | 0 | 0 | — | 141 | 0 | 0 | openai-processing-ms=1078; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 219 | 200 | completed | completed_structured_output | 3852 | 1625 | 252 | 54 | 306 | 0 | 0 | — | 205 | 0 | 0 | openai-processing-ms=1418; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 220 | 200 | completed | completed_structured_output | 3780 | 1225 | 253 | 42 | 295 | 0 | 0 | — | 133 | 0 | 0 | openai-processing-ms=977; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 221 | 200 | completed | completed_structured_output | 3867 | 1350 | 249 | 56 | 305 | 0 | 0 | — | 220 | 0 | 0 | openai-processing-ms=1135; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 222 | 200 | completed | completed_structured_output | 3864 | 1320 | 247 | 55 | 302 | 0 | 0 | — | 217 | 0 | 0 | openai-processing-ms=1097; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 223 | 200 | completed | completed_structured_output | 3820 | 1453 | 251 | 49 | 300 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1138; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 224 | 200 | completed | completed_structured_output | 3781 | 1303 | 250 | 43 | 293 | 0 | 0 | — | 134 | 0 | 0 | openai-processing-ms=1096; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 225 | 200 | completed | completed_structured_output | 3826 | 1405 | 251 | 51 | 302 | 0 | 0 | — | 179 | 0 | 0 | openai-processing-ms=1167; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 226 | 200 | completed | completed_structured_output | 3775 | 1493 | 252 | 39 | 291 | 0 | 0 | — | 128 | 0 | 0 | openai-processing-ms=1198; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 227 | 200 | completed | completed_structured_output | 3798 | 1217 | 250 | 44 | 294 | 0 | 0 | — | 151 | 0 | 0 | openai-processing-ms=949; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 228 | 200 | completed | completed_structured_output | 3786 | 1208 | 251 | 45 | 296 | 0 | 0 | — | 139 | 0 | 0 | openai-processing-ms=994; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 229 | 200 | completed | completed_structured_output | 3870 | 1522 | 252 | 58 | 310 | 0 | 0 | — | 223 | 0 | 0 | openai-processing-ms=1268; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 230 | 200 | completed | completed_structured_output | 3804 | 1389 | 252 | 45 | 297 | 0 | 0 | — | 157 | 0 | 0 | openai-processing-ms=1075; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 231 | 200 | completed | completed_structured_output | 3849 | 1441 | 253 | 56 | 309 | 0 | 0 | — | 202 | 0 | 0 | openai-processing-ms=1210; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 232 | 200 | completed | completed_structured_output | 3872 | 1353 | 249 | 53 | 302 | 0 | 0 | — | 225 | 0 | 0 | openai-processing-ms=1123; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 233 | 200 | completed | completed_structured_output | 3823 | 1091 | 247 | 46 | 293 | 0 | 0 | — | 176 | 0 | 0 | openai-processing-ms=884; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 234 | 200 | completed | completed_structured_output | 3834 | 1409 | 251 | 50 | 301 | 0 | 0 | — | 187 | 0 | 0 | openai-processing-ms=1001; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 235 | 200 | completed | completed_structured_output | 3822 | 1508 | 250 | 50 | 300 | 0 | 0 | — | 175 | 0 | 0 | openai-processing-ms=1139; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 236 | 200 | completed | completed_structured_output | 3820 | 1310 | 251 | 51 | 302 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1063; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 237 | 200 | completed | completed_structured_output | 3797 | 1249 | 252 | 43 | 295 | 0 | 0 | — | 150 | 0 | 0 | openai-processing-ms=1035; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 238 | 200 | completed | completed_structured_output | 3804 | 1409 | 250 | 47 | 297 | 0 | 0 | — | 157 | 0 | 0 | openai-processing-ms=1068; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 239 | 200 | completed | completed_structured_output | 3778 | 1282 | 251 | 40 | 291 | 0 | 0 | — | 131 | 0 | 0 | openai-processing-ms=1077; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 240 | 200 | completed | completed_structured_output | 3854 | 1495 | 248 | 54 | 302 | 0 | 0 | — | 207 | 0 | 0 | openai-processing-ms=1286; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 241 | 200 | completed | completed_structured_output | 3839 | 1370 | 252 | 52 | 304 | 0 | 0 | — | 192 | 0 | 0 | openai-processing-ms=1146; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 242 | 200 | completed | completed_structured_output | 3819 | 1427 | 253 | 47 | 300 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1128; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 243 | 200 | completed | completed_structured_output | 3829 | 1340 | 249 | 48 | 297 | 0 | 0 | — | 182 | 0 | 0 | openai-processing-ms=1133; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 244 | 200 | completed | completed_structured_output | 3820 | 1390 | 247 | 46 | 293 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1157; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 245 | 200 | completed | completed_structured_output | 3805 | 1270 | 251 | 50 | 301 | 0 | 0 | — | 158 | 0 | 0 | openai-processing-ms=1042; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 246 | 200 | completed | completed_structured_output | 3781 | 1323 | 250 | 42 | 292 | 0 | 0 | — | 134 | 0 | 0 | openai-processing-ms=1090; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 247 | 200 | completed | completed_structured_output | 3815 | 1252 | 251 | 47 | 298 | 0 | 0 | — | 168 | 0 | 0 | openai-processing-ms=1010; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 248 | 200 | completed | completed_structured_output | 3812 | 1283 | 252 | 47 | 299 | 0 | 0 | — | 165 | 0 | 0 | openai-processing-ms=1041; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 249 | 200 | completed | completed_structured_output | 3806 | 1230 | 250 | 44 | 294 | 0 | 0 | — | 159 | 0 | 0 | openai-processing-ms=998; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 250 | 200 | completed | completed_structured_output | 3794 | 1403 | 251 | 42 | 293 | 0 | 0 | — | 147 | 0 | 0 | openai-processing-ms=1196; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 251 | 200 | completed | completed_structured_output | 3800 | 1297 | 248 | 46 | 294 | 0 | 0 | — | 153 | 0 | 0 | openai-processing-ms=1063; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 252 | 200 | completed | completed_structured_output | 3837 | 1241 | 252 | 54 | 306 | 0 | 0 | — | 190 | 0 | 0 | openai-processing-ms=1013; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 253 | 200 | completed | completed_structured_output | 3861 | 1362 | 253 | 54 | 307 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1049; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 254 | 200 | completed | completed_structured_output | 3847 | 1435 | 249 | 52 | 301 | 0 | 0 | — | 200 | 0 | 0 | openai-processing-ms=1176; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 255 | 200 | completed | completed_structured_output | 3766 | 1345 | 247 | 37 | 284 | 0 | 0 | — | 119 | 0 | 0 | openai-processing-ms=1111; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 256 | 200 | completed | completed_structured_output | 3814 | 1243 | 251 | 50 | 301 | 0 | 0 | — | 167 | 0 | 0 | openai-processing-ms=938; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 257 | 200 | completed | completed_structured_output | 3798 | 1331 | 250 | 45 | 295 | 0 | 0 | — | 151 | 0 | 0 | openai-processing-ms=1004; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 258 | 200 | completed | completed_structured_output | 3808 | 1354 | 251 | 50 | 301 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=1144; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 259 | 200 | completed | completed_structured_output | 3795 | 1211 | 252 | 45 | 297 | 0 | 0 | — | 148 | 0 | 0 | openai-processing-ms=980; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 260 | 200 | completed | completed_structured_output | 3817 | 1317 | 250 | 49 | 299 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=1083; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 261 | 200 | completed | completed_structured_output | 3823 | 1211 | 251 | 51 | 302 | 0 | 0 | — | 176 | 0 | 0 | openai-processing-ms=983; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 262 | 200 | completed | completed_structured_output | 3837 | 1255 | 248 | 51 | 299 | 0 | 0 | — | 190 | 0 | 0 | openai-processing-ms=1046; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 263 | 200 | completed | completed_structured_output | 3861 | 1543 | 252 | 60 | 312 | 0 | 0 | — | 214 | 0 | 0 | openai-processing-ms=1234; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 264 | 200 | completed | completed_structured_output | 3816 | 1413 | 252 | 49 | 301 | 0 | 0 | — | 169 | 0 | 0 | openai-processing-ms=1091; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 265 | 200 | completed | completed_structured_output | 3866 | 1430 | 249 | 60 | 309 | 0 | 0 | — | 219 | 0 | 0 | openai-processing-ms=1230; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 266 | 200 | completed | completed_structured_output | 3787 | 1257 | 247 | 42 | 289 | 0 | 0 | — | 140 | 0 | 0 | openai-processing-ms=1033; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 267 | 200 | completed | completed_structured_output | 3826 | 1298 | 251 | 49 | 300 | 0 | 0 | — | 179 | 0 | 0 | openai-processing-ms=1089; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 268 | 200 | completed | completed_structured_output | 3781 | 1190 | 250 | 42 | 292 | 0 | 0 | — | 134 | 0 | 0 | openai-processing-ms=967; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 269 | 200 | completed | completed_structured_output | 3830 | 1310 | 251 | 51 | 302 | 0 | 0 | — | 183 | 0 | 0 | openai-processing-ms=1102; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 270 | 200 | completed | completed_structured_output | 3802 | 1432 | 252 | 46 | 298 | 0 | 0 | — | 155 | 0 | 0 | openai-processing-ms=1203; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 271 | 200 | completed | completed_structured_output | 3786 | 1299 | 250 | 43 | 293 | 0 | 0 | — | 139 | 0 | 0 | openai-processing-ms=899; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 272 | 200 | completed | completed_structured_output | 3777 | 1420 | 251 | 40 | 291 | 0 | 0 | — | 130 | 0 | 0 | openai-processing-ms=1019; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 273 | 200 | completed | completed_structured_output | 3809 | 1629 | 248 | 48 | 296 | 0 | 0 | — | 162 | 0 | 0 | openai-processing-ms=1348; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 274 | 200 | completed | completed_structured_output | 3769 | 1151 | 252 | 39 | 291 | 0 | 0 | — | 122 | 0 | 0 | openai-processing-ms=921; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 275 | 200 | completed | completed_structured_output | 3807 | 1222 | 252 | 47 | 299 | 0 | 0 | — | 160 | 0 | 0 | openai-processing-ms=1016; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 276 | 200 | completed | completed_structured_output | 3863 | 1271 | 253 | 58 | 311 | 0 | 0 | — | 216 | 0 | 0 | openai-processing-ms=963; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 277 | 200 | completed | completed_structured_output | 3860 | 1230 | 247 | 53 | 300 | 0 | 0 | — | 213 | 0 | 0 | openai-processing-ms=993; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 278 | 200 | completed | completed_structured_output | 3820 | 1298 | 251 | 50 | 301 | 0 | 0 | — | 173 | 0 | 0 | openai-processing-ms=1053; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 279 | 200 | completed | completed_structured_output | 3812 | 1518 | 250 | 48 | 298 | 0 | 0 | — | 165 | 0 | 0 | openai-processing-ms=961; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 280 | 200 | completed | completed_structured_output | 3813 | 1240 | 251 | 47 | 298 | 0 | 0 | — | 166 | 0 | 0 | openai-processing-ms=937; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 281 | 200 | completed | completed_structured_output | 3817 | 1183 | 252 | 47 | 299 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=975; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 282 | 200 | completed | completed_structured_output | 3792 | 1055 | 250 | 45 | 295 | 0 | 0 | — | 145 | 0 | 0 | openai-processing-ms=845; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 283 | 200 | completed | completed_structured_output | 3768 | 1200 | 251 | 38 | 289 | 0 | 0 | — | 121 | 0 | 0 | openai-processing-ms=969; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 284 | 200 | completed | completed_structured_output | 3852 | 1326 | 248 | 52 | 300 | 0 | 0 | — | 205 | 0 | 0 | openai-processing-ms=1094; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 285 | 200 | completed | completed_structured_output | 3784 | 1277 | 252 | 41 | 293 | 0 | 0 | — | 137 | 0 | 0 | openai-processing-ms=1042; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 286 | 200 | completed | completed_structured_output | 3832 | 1386 | 252 | 54 | 306 | 0 | 0 | — | 185 | 0 | 0 | openai-processing-ms=1155; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 287 | 200 | completed | completed_structured_output | 3842 | 1494 | 253 | 53 | 306 | 0 | 0 | — | 195 | 0 | 0 | openai-processing-ms=1288; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 288 | 200 | completed | completed_structured_output | 3841 | 1401 | 249 | 52 | 301 | 0 | 0 | — | 194 | 0 | 0 | openai-processing-ms=1179; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 289 | 200 | completed | completed_structured_output | 3819 | 1423 | 251 | 52 | 303 | 0 | 0 | — | 172 | 0 | 0 | openai-processing-ms=1102; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 290 | 200 | completed | completed_structured_output | 3829 | 1552 | 250 | 54 | 304 | 0 | 0 | — | 182 | 0 | 0 | openai-processing-ms=1219; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 291 | 200 | completed | completed_structured_output | 3818 | 1285 | 251 | 48 | 299 | 0 | 0 | — | 171 | 0 | 0 | openai-processing-ms=1050; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 292 | 200 | completed | completed_structured_output | 3788 | 1310 | 252 | 44 | 296 | 0 | 0 | — | 141 | 0 | 0 | openai-processing-ms=1100; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 293 | 200 | completed | completed_structured_output | 3803 | 1547 | 250 | 47 | 297 | 0 | 0 | — | 156 | 0 | 0 | openai-processing-ms=1167; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 294 | 200 | completed | completed_structured_output | 3858 | 4288 | 251 | 56 | 307 | 0 | 0 | — | 211 | 0 | 0 | openai-processing-ms=1735; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 295 | 200 | completed | completed_structured_output | 3821 | 1282 | 248 | 50 | 298 | 0 | 0 | — | 174 | 0 | 0 | openai-processing-ms=968; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 296 | 200 | completed | completed_structured_output | 3808 | 1452 | 252 | 49 | 301 | 0 | 0 | — | 161 | 0 | 0 | openai-processing-ms=1218; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 297 | 200 | completed | completed_structured_output | 3824 | 6402 | 252 | 49 | 301 | 0 | 0 | — | 177 | 0 | 0 | openai-processing-ms=5839; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 298 | 200 | completed | completed_structured_output | 3817 | 1339 | 253 | 48 | 301 | 0 | 0 | — | 170 | 0 | 0 | openai-processing-ms=1127; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 299 | 200 | completed | completed_structured_output | 3833 | 1492 | 249 | 50 | 299 | 0 | 0 | — | 186 | 0 | 0 | openai-processing-ms=1257; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |
| 300 | 200 | completed | completed_structured_output | 3858 | 1322 | 247 | 52 | 299 | 0 | 0 | — | 211 | 0 | 0 | openai-processing-ms=1093; x-ratelimit-limit-requests=500; x-ratelimit-limit-tokens=200000; x-ratelimit-remaining-requests=499; x-ratelimit-remaining-tokens=200000; x-ratelimit-reset-requests=120ms; x-ratelimit-reset-tokens=0s |

</details>

## Reproduce

Credential-free plan:

```bash
LIVE_AI_PROVIDER=openai \
LIVE_AI_EVAL_REPETITIONS=25 \
LIVE_AI_EVAL_MAX_CALLS=300 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
OPENAI_LIVE_MODEL='gpt-5.6-luna' \
./gradlew --no-daemon liveAiEval --args='--plan'
```

Authorized paid run (supply the credential only through the environment):

```bash
LIVE_AI_PROVIDER=openai \
LIVE_AI_EVAL_REPETITIONS=25 \
LIVE_AI_EVAL_MAX_CALLS=300 \
LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=0 \
LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=0.01 \
RUN_LIVE_AI_TESTS=true \
OPENAI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \
LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \
LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \
OPENAI_LIVE_MODEL='gpt-5.6-luna' \
OPENAI_API_KEY="$(<REPLACE_WITH_APPROVED_SECRET_FILE)" \
./gradlew --no-daemon liveAiEval
```

`REPLACE_WITH_APPROVED_SECRET_FILE` is a placeholder; use an approved local secret source without printing or committing the credential.

## Interpretation limits

- The Wilson result is an overall result for this fixed, equally weighted synthetic corpus and the provider conditions during this run.
- Per-case samples contain fewer observations and must not be read as simultaneous 95% guarantees.
- Passing measures structural validity, local grounding, request-boundary compliance, and metering evidence; it does not grade creativity or relevance.
- Provider usage is evidence of metered processing. Actual billed cost requires the provider billing export.
