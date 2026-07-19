# Persons Finder Agent Guidance

## Authority and scope

- Read `README.md` and `docs/REQUIREMENTS_TRACEABILITY.md` before work. The challenge requirements in `README.md` are authoritative and must remain intact when run or setup documentation is added; only human-approved ledger entries resolve ambiguity. Open ledger rows remain open.
- The mandatory core routes are exactly `POST /persons`, `PUT /persons/{id}/location`, and `GET /persons/nearby`. Do not add versioned or compatibility aliases.
- Finish mandatory core behavior, including approved validation, prompt-injection resistance, PII and egress controls, required security documentation, and bio failure atomicity, before benchmark, hardening beyond the mandatory contract, auth, sharing, mobile, admin, search, map, or history extensions. Label non-core work `deferred` or `extension`.

## Architecture boundaries

- During normal operation, accepted location observations are append-only. Nearby reads from a transactionally maintained, rebuildable one-row-per-person last-known projection. Do not infer retention, erasure, purge, or restore policy from this rule.
- Keep bio generation behind an application-owned, provider/model-neutral `BioGenerator`. Keep provider SDK types, model IDs, credentials, wire types, prompt syntax, and vendor failures out of controllers, use cases, domain models, persistence, and public schemas.
- Enforce the approved PII and egress allowlist. Only approved sanitized context may cross a remote model boundary; never egress raw customer input, coordinates, identity, access tokens, or person-derived transport metadata. Never log prompt, response, or customer content; application logs are metadata-only.
- A bio policy, generation, validation, or persistence failure must leave no partial person/location state. Invalid adapter configuration fails clearly; runtime provider fallback is forbidden.

## Working rules

- Inspect relevant source, configuration, tests, documentation, and git state before editing. Preserve user work and keep diffs narrow.
- Do not inspect, copy, or adapt another candidate's Persons Finder submission without written assessment-owner permission.
- Never commit or push without explicit human approval of the exact scope.
- Keep local journals, handoffs, secrets, raw transcripts, PII, logs, and generated scratch evidence under ignored local paths.

## Truth and evidence

- Use `planned` for proposed work, `implemented` only for present code/configuration, and `verified` only for behavior supported by evidence executed against the current state. Inspection and implementation are not verification.
- Do not claim runtime, dependency, container image, Docker, PostGIS, migration, query-plan, security-control, benchmark, or clean-clone success without executable evidence from the approved environment.
- Keep the runtime and default PostGIS artifact evidence-gated. Docker proof must cover native arm64 and amd64, loopback-only backend exposure, an internal database without a host port, Flyway/readiness gating, named-volume persistence, and no host database, OIDC, or external AI credential.
- Verify in this order when authorized: focused unit checks, real PostGIS repository integration, HTTP smoke/contract checks, then clean Docker/clone evidence. Add automation only after its underlying commands pass.
- Preserve only truthful prompts, proposals, human decisions, diffs, failures, corrections, and verification. Do not request hidden chain-of-thought or create transcript dumps, fabricated mistakes, decorative agents, or placeholder-green checks.

## Future task prompts

Only when explicitly asked to draft a future task prompt, use each heading exactly once and include one authorization boundary:

1. Goal
2. Relevant context
3. Authorization boundary
4. Constraints
5. Required evidence
6. Success criteria
7. Output shape

Request decision-first responses with material caveats and next actions.
