# Security and Privacy

This document describes controls present in the core candidate and separates
them from production controls that have not been implemented. It is not an
external audit or certification.

## Implemented core controls

### API input and error handling

- Request bodies reject unknown JSON properties. The nearby endpoint also
  rejects unknown, repeated, missing, empty, non-finite, and out-of-range query
  parameters.
- Profile text is validated by Unicode code-point bounds, trimmed, and stored in
  NFC form. Controls, line/paragraph separators, malformed text, and most
  Unicode format controls are rejected; ZWJ and ZWNJ remain allowed for
  legitimate scripts and emoji.
- A separate NFKC security view of job titles and hobbies is canonically
  decomposed with combining marks, default-ignorable characters, and variation
  selectors removed, then checked individually and across field boundaries for
  the approved narrow instruction-manipulation and explicit
  credential/identifier patterns. Names are not scanned because they never
  enter remote generation. Ordinary validation failures return 400; unsafe bio
  source input returns 422.
- Error responses use bounded `application/problem+json` fields and stable
  codes. They do not include submitted values, coordinates, stack traces, or
  provider details.

### Bio generation, prompt injection, and egress

The default core runtime uses a deterministic, credential-free `BioGenerator`.
The code also contains an opt-in remote adapter, but the tracked Compose stack
does not enable it or mount model credentials.

Before any remote call, `BioPolicy` maps exact reviewed aliases into an
application-owned `BioTemplateRequest`. Its outbound allowlist contains only:

- literal `{{NAME}}`, locale `en-NZ`, country `NZ`, and tone `quirky`;
- one broad job-category code and mapping version; and
- nonempty, deduplicated broad interest codes and their mapping version.

The optional macro-region field is disabled by default. Raw names, job titles,
employers, hobbies, places, coordinates, person or observation identifiers,
credentials, and person-derived transport metadata are not fields in the
outbound request. Unmatched or sensitive source values map to the broad
`other` code.

The remote clients request a JSON object containing only `bio_template`. The
application rejects an oversized response, malformed JSON, duplicate keys,
trailing content, extra fields, non-string values, and prose outside the
deterministic template contract. OpenAI additionally receives a strict string
pattern that permits each of the six possible orders of the three required
placeholders while requiring each placeholder exactly once. This provider-side
constraint reduces invalid generations; the provider-neutral application
validator remains authoritative. The calibrated request allows up to 256
provider output tokens. Accepted prose must:

- contain one to three sentences;
- contain exactly one `{{NAME}}`, `{{JOB}}`, and `{{HOBBY}}`;
- contain no unknown placeholder or forbidden region;
- contain no standalone model-authored `prompt`, `prompts`, `instruction`, or
  `instructions` meta-language; word boundaries preserve benign words such as
  `promptly` and `instructional`;
- contain printable ASCII only; and
- stay within 512 non-placeholder Unicode code points.

A trusted one-pass composer then inserts the validated local name, job title,
and selected original hobby as opaque values. It checks exact grounding and a
final 732-code-point limit: the 512-point prose allowance plus the existing
220-point maximum for the selected local values. These limits retain substantial
headroom over the paid calibration maxima without relaxing the sentence,
placeholder, character, policy, or grounding checks. The model-authored
meta-language policy is applied before composition, so matching words in opaque
validated local source values are not reinterpreted. Model output remains
untrusted data, not an instruction or authorization source.

Bio policy, generation, parsing, validation, and composition all complete
before the person/location database transaction begins. A failure leaves no
partial person, observation, or last-known-location projection. Invalid output
maps to 502, unavailability/timeout/rate limiting maps to 503, cancellation
propagates, and there is no automatic provider or deterministic fallback.

### Credentials, transport, storage, and containers

- The core Compose stack uses an ignored local database-password file mounted
  as a Compose secret; it is not placed in the resolved Compose environment.
- Remote provider credentials, when that non-default adapter is explicitly
  selected, are read from environment or config-tree secrets and sent in
  provider authentication headers rather than request JSON. Invalid provider,
  model, credential, timeout, or runtime combinations fail startup.
- Provider clients use fixed HTTPS endpoints, do not follow redirects, enforce
  bounded timeouts, and stop before buffering more than 262,144 response bytes.
  Request/response diagnostic representations omit headers and bodies.
- The application container runs as a dedicated non-root user with a read-only
  root filesystem, a bounded `/tmp` tmpfs, and `no-new-privileges`. The database
  container runs as the `postgres` user with `no-new-privileges`.
- Compose publishes the application on IPv4 loopback only. The database is on
  an internal network with no host port. Flyway owns the schema migrations, and
  accepted location observations are append-only during normal operation.

## Executable security checks

`./scripts/verify.sh` is credential-free and explicitly removes live-provider
configuration from its environment. In addition to the full Gradle build and
HTTP/PostGIS smoke, it:

- runs focused bio adapter, privacy-boundary, provider-contract, hostile-output,
  timeout, and failure-atomicity tests;
- confirms the application binds to loopback and the database has no host port;
- checks the resolved Compose configuration and captured container logs for the
  generated database password and fixed synthetic profile values; and
- records `live_provider_calls=disabled` in
  `build/verification/summary.txt`.

Those value checks cover the generated secret and known smoke fixtures; they
are not a general proof that arbitrary secrets or personal data can never be
logged.

The repository also configures GitHub Actions to run Trivy 0.72.0 against
repository misconfiguration and built application/PostGIS images, failing on
high or critical findings under the workflow's stated filters. That workflow is
CI configuration, not evidence that a scan passed for an unpushed local
revision.

## Third-party PII and model risk

Sending names, precise locations, employment details, hobbies, identifiers, or
linkable request metadata to a third-party model can enable profiling,
re-identification, cross-request correlation, unintended retention, regulatory
exposure, or disclosure through provider operations and abuse monitoring.
Prompt filtering alone cannot make a model trusted.

The implemented allowlist reduces data sent to a provider, but broad categories
and ordinary network/provider metadata can still reveal information. Provider
retention, residency, subprocessors, training/product-improvement use, access
controls, contractual terms, and incident response require organizational
review before any production use.

## Known gaps in this core candidate

The following controls are not implemented:

- production-wide reliability evidence for the remote model. Three independent
  paid OpenAI fixed-corpus runs were completed without retries, top-ups, or
  fallback and are not pooled. Revision `465c648` produced 299/300 valid and
  failed the 1% Wilson gate after one request reached the prior 10-second
  deadline. Revision `7e02d65dc2895e6e618365021053c96f78ec8efb` then passed
  300/300 under the 15-second deadline, but its intentionally content-free
  evidence cannot reclassify those outputs under the later, stricter
  model-authored meta-language policy. Final-policy revision
  `316be4ab57c424aae4fbb5a2ecc9b43e2fb612da` independently repeated the exact
  12-by-25 protocol under the 15-second deadline and produced 300/300 valid
  results and 298 distinct outputs, with a 0.8938% one-sided 95% Wilson upper
  bound. All 300 responses were HTTP 200 and completed; p50/p95/max
  application latency was 1.330/2.705/6.404 seconds. The final run reported
  75,150 input and 14,595 output tokens and an estimated USD 0.162720 cost.
  Across all paid evidence, 945 calls reported 236,485 input and 46,011 output
  tokens, for an estimated USD 0.512551 rather than an actual billing claim.
  Only the final-policy run gates its exact revision. The
  [content-safe report](docs/evidence/live-ai/openai-316be4ab57c424aae4fbb5a2ecc9b43e2fb612da-12x25-passed.md)
  records the fixed synthetic conditions. This evidence does not predict all
  future provider or production behavior;
- authentication, authorization, tenant isolation, or ownership checks;
- application-level rate limiting, quotas, or abuse prevention;
- TLS termination for the local HTTP API;
- customer consent, retention, deletion, erasure, or restore workflows;
- production secrets management, workload identity, or credential rotation;
- tamper-evident audit logging, security monitoring, and incident response; and
- native amd64 runtime verification of the Docker stack.

The local Compose stack must not be treated as an internet-facing production
deployment.

## Higher-security banking architecture (proposed, not implemented)

For a high-security banking deployment, prefer deterministic generation inside
the bank boundary or a bank-controlled model. If external inference is approved,
place it behind a dedicated egress service that:

- accepts only a versioned schema of coarse non-identifying codes and rejects
  unknown fields;
- allowlists destinations and blocks all other egress;
- uses workload identity and a managed secrets service with rotation;
- enforces encrypted transport, strict timeouts, quotas, circuit breaking, and
  authenticated callers;
- records only privacy-reviewed metadata in tamper-evident audit events;
- enforces approved retention, residency, subprocessor, and deletion terms; and
- undergoes threat modelling, privacy impact assessment, red-team testing,
  output monitoring, and incident-response exercises.

Customer consent, purpose limitation, authorization, retention/deletion policy,
and legal approval remain human-owned controls. A model response must never
authorize a transaction or change a security decision.
