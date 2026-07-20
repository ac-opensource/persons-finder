# Security and Privacy

A worked example of how these controls can be reported is available in
[`docs/SECURITY_REPORT_SAMPLE.md`](docs/SECURITY_REPORT_SAMPLE.md). It is
explicitly scoped as an internal sample, not an external audit or certification.

## Implemented bio-generation boundary

`POST /persons` validates and canonicalizes profile fields before bio
generation. A narrow source policy rejects deterministic instruction-
manipulation patterns and explicit credential or identifier patterns in job
titles and hobbies. The policy scans an NFKC-normalized security copy with
default-ignorable format characters and variation selectors removed, while
stored profile text remains NFC-canonical.
Names are not scanned by that heuristic because names never enter remote bio
generation. Profile validation rejects controls, separators, and Unicode format
controls used for bidi or invisible-text deception; ZWJ and ZWNJ remain allowed
for legitimate scripts and emoji.

The application does not send raw profile fields to a remote model. `BioPolicy`
maps exact, reviewed whole-value aliases into a closed `BioTemplateRequest`.
The outbound allowlist is limited to:

- the literal display-name token `{{NAME}}`;
- deployment locale `en-NZ` and country code `NZ`;
- one broad job-category code plus its mapping version;
- nonempty, deduplicated broad interest codes plus their mapping version;
- the fixed `quirky` tone; and
- an optional closed macro-region code, which is currently default-off.

Raw names, job titles, employers, hobbies, places, coordinates, person or
observation identifiers, credentials, and person-derived transport metadata
cannot be represented by this request type. Sensitive, rare, or unmatched job
and hobby values map to the broad `other` code.

The remote adapter serializes that allowlist as data and instructs the model to
treat every field as inert. OpenAI, Gemini, and Anthropic use provider-native
structured JSON output constraints for an object containing only
`bio_template`, whose value is model-authored prose. This is defence in depth,
not trust in the provider: the application caps the output size, enables
duplicate-key detection, and rejects malformed JSON, trailing content, extra
fields, non-string values, or prose that fails the deterministic contract.

The application boundary independently normalizes and validates the
provider-authored template. It must contain one to three safe sentences,
exactly one literal `{{NAME}}`, `{{JOB}}`, and `{{HOBBY}}`, no unknown token,
no forbidden region disclosure, printable ASCII only, and no more than 100
non-placeholder code points. A trusted parser then renders the validated name,
raw job title, and selected original hobby once as opaque segments without
rescanning inserted text. Grounding and final validation run after composition,
including the 320-Unicode-code-point limit.

Bio policy, provider, parsing, validation, or composition failure occurs before
the person/location transaction starts, so it leaves no partial state. Provider
errors are normalized centrally: unsafe source input is 422, invalid or unsafe
generated output is 502, and timeout/rate-limit/unavailability is 503.
Cancellation propagates instead of becoming an application error.
Configuration selects exactly one provider and model, and there is no
cross-provider or deterministic runtime fallback.

## Credentials, transport, and observability

Provider credentials are read from environment or mounted-secret
configuration. They are sent only in the provider authorization header and are
never included in request JSON. Invalid remote configuration fails startup.

Provider endpoints are fixed HTTPS origins in the respective clients. Redirects
are disabled, calls have bounded timeouts, and a back-pressured HTTP body
subscriber cancels before buffering more than 65,536 response bytes.
Request/response diagnostic representations omit headers and bodies.
Application logs must remain metadata-only: do not log profile fields,
coordinates, prompts, provider responses, generated bios, or credentials.

The evaluator-default runtime remains deterministic, offline, and
credential-free. Remote generation is an explicit network/private opt-in. That
mode name is a deployment precondition, not an access-control mechanism:
deployments must keep it behind authenticated, rate-limited ingress. Public
unauthenticated use could otherwise turn `POST /persons` into a billable-call
amplifier; application-level authentication remains outside the mandatory core.

## Third-party PII and model risk

Sending names, precise locations, employment details, hobbies, identifiers, or
linkable request metadata to a third-party model could enable profiling,
re-identification, cross-request correlation, unintended retention, regulatory
exposure, or disclosure through provider operations and abuse monitoring.
Prompt injection is also not solved by text filtering alone; a model remains an
untrusted nondeterministic dependency.

The implemented minimization boundary substantially reduces this risk but does
not make a third-party provider risk-free. Broad categories can still reveal
limited traits, and network/provider metadata still exists. Provider contracts,
retention controls, residency, subprocessors, access controls, and incident
response require separate organizational review before production use.

For the single, explicitly invoked live evaluation, the human owner separately
accepts provider retention, abuse monitoring, human review, and
product-improvement use, as applicable, of only the fixed synthetic smoke
fixtures, versioned aggregate corpus, and fixed application-owned prompt/schema. The
`*_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED` test gate records that narrow
acceptance; it does not claim logging is disabled, authorize customer-derived
or production data, or satisfy the production controls below.

## Higher-security banking architecture

For a high-security banking deployment, prefer deterministic local generation
or an approved model hosted inside the bank's controlled boundary. If external
inference is approved, place it behind a dedicated egress service that:

- accepts only a versioned schema of coarse, non-identifying codes;
- rejects all unknown fields and blocks every non-allowlisted destination;
- uses workload identity and a managed secrets service with rotation;
- enforces encryption in transit, strict timeouts, quotas, and circuit breaking;
- records only privacy-reviewed metadata and tamper-evident audit events;
- applies contractual zero-retention, regional-processing, and subprocessor
  controls; and
- undergoes threat modelling, privacy impact assessment, red-team testing,
  model/output monitoring, and incident-response exercises.

Customer consent, purpose limitation, deletion/retention policy, access
authorization, and legal approval remain human-owned controls. A model response
must never authorize a transaction or alter a security decision.
