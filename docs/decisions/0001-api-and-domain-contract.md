# ADR 0001: API and domain contract

- Status: accepted
- Date: 2026-07-19
- Delivery state: POST and PUT implemented; nearby remains planned

## Context

The challenge README defines three unversioned endpoints. Starter comments and
types are stale and are not compatibility requirements. This record closes the
API/domain questions approved by the human before controller or product
behavior is implemented.

## Decision

### Routes and public identifiers

The only mandatory routes are:

- `POST /persons`
- `PUT /persons/{id}/location`
- `GET /persons/nearby`

There are no versioned aliases. Public person and server observation IDs are
UUIDv4 values. `clientUpdateId` accepts any non-nil RFC 4122/IETF-variant UUID.
Responses use canonical lowercase UUID text. UUID tie ordering compares the
unsigned RFC network-order bytes, which is compatible with PostgreSQL native
`uuid` ordering.

`POST /persons` returns `201 Created` and the relative response header
`Location: /persons/{id}`. That header value does not add a fourth route.
Every request object, including nested objects and the PUT body, is closed and
rejects unknown fields.

### Create person

The closed request object contains:

```json
{
  "name": "Aroha",
  "jobTitle": "Software engineer",
  "hobbies": ["tramping", "pottery"],
  "location": {
    "latitude": -41.2865,
    "longitude": 174.7762
  }
}
```

`hobbies` is nonempty and duplicate names are allowed. The successful response
contains exactly the public profile fields `id`, `name`, `jobTitle`, `hobbies`,
`bio`, `createdAt`, and `lastKnownLocationAt`. It never exposes stored
coordinates. Creation also appends one server-origin initial observation. One
server-clock value supplies person `createdAt` and that observation's
`capturedAt`/`receivedAt`; the initial observation has no `clientUpdateId`.

Profile text is canonicalized before use: validate well-formed Unicode, trim
outer Unicode whitespace, normalize to NFC, preserve case, internal whitespace,
list order, and original spelling, and reject blank text, control characters,
line/paragraph separators, and malformed Unicode. Limits count Unicode code
points:

- `name`: 1 to 80
- `jobTitle`: 1 to 80
- `hobbies`: 1 to 10 items
- each hobby: 1 to 60
- selected `name` + `jobTitle` + grounding hobby: at most 206

Exact canonical hobby duplicates are removed after the raw 10-item cap,
preserving first-input order. Case-folding and fuzzy matching are not used for
stored source values. The selected-value aggregate reserves 34 code points for
the shortest approved grammatical assessment template
`, a quirky , has a soft spot for .`; it is checked before generation so the
three opaque inserted values can fit the 240-code-point final contract.

### Location updates

Every request requires `latitude` and `longitude`. `capturedAt` and
`clientUpdateId` must appear together or both be absent. This is one API
contract, not a compatibility alias.

Coordinates must be finite and within latitude `[-90, 90]` and longitude
`[-180, 180]`. Canonicalization changes negative zero to positive zero,
longitude `+180` to `-180`, and longitude at either pole to zero. It performs
no other rounding. Equality is exact after canonicalization. Future
persistence has one geographic point source of truth per row:
`geography(Point,4326)` with longitude as X and latitude as Y.
The same validation and canonicalization apply to POST location, PUT location,
and nearby query coordinates.

Accepted observations are immutable normal-operation history. A last-known
projection is transactionally maintained, derived, and rebuildable. The
greatest tuple of validated `capturedAt`, server `receivedAt`, and observation
UUID wins. A late observation remains in history without rewinding that
projection.

The no-key form locks the person's winning projection. If its canonical point
equals the request point, it is an immediate replay and returns that existing
observation without appending. “Immediate” means no intervening winning
coordinate change, not an elapsed-time window; row locking makes concurrent
identical no-key requests converge on one appended observation. A changed
point appends a server-timestamped `NO_KEY` observation.

Reusing `(personId, clientUpdateId)` with the same canonical point and
normalized `capturedAt` returns the original observation. Reuse with different
content returns `409 IDEMPOTENCY_KEY_REUSED`. `receivedAt` is not part of the
idempotency content. A new `clientUpdateId` always represents a new accepted
sample and may append at the same canonical point.

Client `capturedAt` uses a restricted RFC 3339 form: explicit `T`, uppercase
`Z` or a numeric offset, zero to three fractional-second digits, no leap
second, and no precision finer than milliseconds. It is normalized to UTC
milliseconds and may be at most five minutes ahead of server receipt time.
There is no oldest accepted timestamp. Response timestamps are UTC strings in
`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` form, for example
`2026-07-19T01:02:03.000Z`.

A successful PUT returns exactly:

- `personId`
- `observationId`
- `capturedAt`
- `receivedAt`
- `lastKnownObservationId`
- `lastKnownLocationAt`

It does not return coordinates, `clientUpdateId`, or a no-op flag. A replay
returns the original observation fields and the current last-known fields.
`lastKnownLocationAt` is the winning last-known observation's `capturedAt`;
the same meaning applies in POST and nearby responses.

### Nearby search

`GET /persons/nearby` requires exactly one nonempty `lat`, `lon`, and `radius`
query parameter. Unknown, alternate, empty, or repeated query parameters are
invalid. Radius is kilometres with `0 < radius <= 100`.

The response is a bare JSON array, including `[]` when there are no matches.
Every match is returned without a silent cap, including distance zero and the
exact radius boundary. Each item contains the create response fields plus
numeric `distanceKm`, rendered to one decimal place. Membership and ordering
use unrounded spheroidal distance, followed by public person UUID as the stable
tie-break. Stored coordinates are never returned.

### Errors

Errors use sanitized RFC 9457 Problem Details with
`Content-Type: application/problem+json`. Every body contains `type`, `title`,
`status`, `detail`, and `code`; fixed prose must not reveal internals or rejected
values. `instance` is omitted.

| HTTP | Machine code | Stable type |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `urn:persons-finder:problem:validation-failed` |
| 404 | `PERSON_NOT_FOUND` | `urn:persons-finder:problem:person-not-found` |
| 409 | `IDEMPOTENCY_KEY_REUSED` | `urn:persons-finder:problem:idempotency-key-reused` |
| 422 | `BIO_INPUT_REJECTED` | `urn:persons-finder:problem:bio-input-rejected` |
| 503 | `BIO_GENERATION_UNAVAILABLE` | `urn:persons-finder:problem:bio-generation-unavailable` |

Only `VALIDATION_FAILED` includes `violations`. Each violation has `field` and
`code`; the list is sorted by field and then code. Initial violation codes are
`REQUIRED`, `INVALID_TYPE`, `INVALID_FORMAT`, `UNKNOWN_FIELD`, `OUT_OF_RANGE`,
`TOO_LONG`, `TOO_MANY_ITEMS`, and `DUPLICATE_ITEM`.

Ordinary structural and domain validation runs first, so `400` wins when a
request also contains potentially malicious bio source text. `422` is reserved
for an otherwise valid `jobTitle` or hobby matching a narrow deterministic,
high-confidence policy: instruction override/reveal/impersonation or explicit
role/control-token injection; or a credential, private key, bearer/API token,
JWT, email address, phone number, or URL. Placeholder-looking source text by
itself remains valid because local composition treats it as opaque. `name` is
not scanned. Fuzzy suspicion, toxicity, identity, and sensitive or rare hobby
topics are not rejection criteria.

### Domain and application boundaries

Canonical profile text, public IDs, geographic points, client timestamps,
immutable observations, and last-known winner selection are shared domain
concepts. Search radius is a nearby-specific domain concept. Public
request/response classes remain separate from persistence rows.

The service uses feature-first hexagonal architecture with use-case slices.
Create and update each own their public schemas, controller, application
service, outcomes, and repository port. One shared direct-JDBC command
adapter implements those two ports because both use cases change the same
transactional person, observation, and last-known model. Nearby owns a separate
query service, repository port, and PostGIS/JDBC adapter; nearby reads must
not expand the shared command repository into a general persistence hub.
Bio generation uses an application-owned, provider/model-neutral
`BioGenerator` request/result/failure contract. `BioTemplateRequest` can contain
only literal `{{NAME}}`, constants `en-NZ` and `NZ`, required `job-v1` broad job
code, nonempty deduplicated `interest-v1` broad interest codes, optional closed
macro-region, and `quirky` tone. It has no property capable of carrying a real
name, source job/hobby text, location, identifier, token, or arbitrary
customer context.

`SafeJobCode` v1 is exactly `technology_engineering`, `healthcare`,
`education_research`, `creative_media`, `business_operations`,
`finance_legal`, `sales_service`, `trades_manufacturing`,
`hospitality_retail`, `public_community_service`, `student`, and `other`.
`SafeInterestCode` v1 is exactly `outdoors_nature`, `sports_fitness`,
`arts_crafts`, `music`, `reading_writing`, `food_drink`, `games_puzzles`,
`technology_making`, `gardening`, `travel`, and `other`.

Reviewed aliases match the entire locally normalized source value only; there
is no fuzzy, substring, or cross-field inference. Unmapped benign values map to
`other`. Sensitive or rare topics get no specific alias and remain accepted
local source values. Hobbies map in order, outbound codes deduplicate by first
appearance, and the grounding hobby is the first reviewed exact alias or,
when none exists, the first benign unmatched hobby. The both-`other` path is an
intentional generic assessment degradation.

Actual instruction manipulation and explicit secrets/identifiers in otherwise
valid job/hobby input are rejected. The typed request is the hard egress
guarantee; content detection is additional defence only. `macroRegion` is
always omitted in this slice. Locale/country are deployment output context and
never assert customer location, nationality, or identity.

Generator output must contain exactly one each of `{{NAME}}`, `{{JOB}}`, and
`{{HOBBY}}`, no unknown placeholder, controls, or disallowed island wording.
A trusted local parser renders each token once with the validated original
value as opaque text and never rescans inserted text. The final result must be
one nonblank sentence of at most 240 Unicode code points.

Any future network adapter must preserve this allowlist, log metadata only,
normalize failures, and never silently fall back. Unknown adapter
configuration fails startup/readiness. The deterministic credential-free
adapter is selected only for tests and the assessment-local runtime. Bio
generation and validation complete before the transaction that writes the
person, initial observation, and projection.

## Consequences

- Controllers can remain thin and map between public schemas and invariant
  domain/application types.
- Capability-oriented packages expose create, update-location, and nearby use
  cases without top-level controller/service/repository buckets.
- Shared command persistence remains explicit, while nearby query SQL and its
  performance lifecycle stay owned by the nearby slice.
- Location updates have one both-or-neither contract with defined no-key and
  keyed retry semantics.
- Exact wire errors and privacy rules are testable without persistence.
- PostgreSQL/PostGIS persistence uses Flyway-owned schema and explicit JDBC.

## Out of scope

The public nearby HTTP endpoint, its GiST index/migration, complete spatial-edge
evidence, external bio adapters, authentication, mobile code, and deferred
product features are outside this slice. The nearby query port and JDBC/PostGIS
adapter are structured separately so that follow-on work cannot grow the shared
command repository. Retention, erasure, purge repair, post-purge replay,
restore, receipt/HMAC design, and key rotation remain lifecycle questions
requiring separate human approval.

## Planned evidence

- Focused controller checks for POST/PUT routes, bodies, headers, response
  privacy, query strictness, and Problem Details.
- Focused domain checks for canonicalization, bounds, catalogs, source policy,
  template parsing/composition, timestamps, and coordinates.
- Fresh real-PostGIS Flyway/schema checks plus transaction, rollback, keyed
  replay/conflict, late-event, and concurrent no-key evidence.
- Focused real-PostGIS evidence for the separately owned nearby query adapter;
  its public controller, GiST plan, and full edge matrix remain follow-on work.
