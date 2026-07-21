#!/usr/bin/env bash

set -Eeuo pipefail

# Ordinary verification disables every known live-provider selector, credential,
# and authorization flag before invoking the project-controlled Gradle wrapper.
# This prevents accidental provider calls; it is not a sandbox for arbitrary
# build or test code and does not claim to scrub unrelated host credentials.
unset \
    RUN_LIVE_AI_TESTS \
    LIVE_AI_PROVIDER \
    LIVE_AI_EVAL_REPETITIONS \
    LIVE_AI_EVAL_MAX_CALLS \
    LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND \
    LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS \
    LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED \
    LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED \
    LIVE_AI_COMPLETE_ENVELOPE_INSPECTION_CONFIRMED \
    OPENAI_API_KEY \
    OPENAI_LIVE_MODEL \
    OPENAI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED \
    GEMINI_API_KEY \
    GEMINI_LIVE_MODEL \
    GEMINI_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED \
    ANTHROPIC_API_KEY \
    ANTHROPIC_LIVE_MODEL \
    ANTHROPIC_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED \
    PERSONS_BIO_REMOTE_PROVIDER \
    PERSONS_BIO_REMOTE_MODEL \
    PERSONS_BIO_REMOTE_TIMEOUT

export PERSONS_BIO_GENERATOR=deterministic
export PERSONS_RUNTIME_MODE=assessment-local

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="$ROOT_DIR/build/verification"
RUN_ID="$(date -u +%Y%m%d%H%M%S)-$$"
COMPOSE_PROJECT="persons-finder-verify-$RUN_ID"
umask 077
mkdir -p "$ROOT_DIR/.secrets"
chmod 0700 "$ROOT_DIR/.secrets"
RUN_DIR="$(mktemp -d "$ROOT_DIR/.secrets/persons-finder-verify.XXXXXX")"
COMPOSE_OVERRIDE="$RUN_DIR/compose.verify.yaml"
DATABASE_PASSWORD_FILE="$RUN_DIR/database-password"
DATABASE_PASSWORD_RELATIVE_FILE="${DATABASE_PASSWORD_FILE#"$ROOT_DIR/"}"
FORBIDDEN_LOG_VALUES="$RUN_DIR/forbidden-log-values"
CURRENT_PHASE="initialization"
COMPOSE_CONFIGURED=0

cd "$ROOT_DIR"

compose() {
    docker compose \
        --project-name "$COMPOSE_PROJECT" \
        --file "$ROOT_DIR/compose.yaml" \
        --file "$COMPOSE_OVERRIDE" \
        "$@"
}

fail() {
    printf 'verification failed: %s\n' "$*" >&2
    return 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

capture_compose_evidence() {
    mkdir -p "$ARTIFACT_DIR"
    compose ps --all >"$ARTIFACT_DIR/compose-ps.txt" 2>&1 || true
    compose logs --no-color --timestamps >"$ARTIFACT_DIR/compose.log" 2>&1 || true
}

finish() {
    local status=$?
    local cleanup_status=0

    trap - EXIT INT TERM
    set +e

    if [[ "$COMPOSE_CONFIGURED" -eq 1 ]]; then
        capture_compose_evidence
        compose down --volumes --remove-orphans --rmi all
        cleanup_status=$?
    fi

    if [[ "$status" -eq 0 && "$cleanup_status" -ne 0 ]]; then
        status="$cleanup_status"
    fi

    mkdir -p "$ARTIFACT_DIR"
    {
        printf 'status=%s\n' "$status"
        printf 'last_phase=%s\n' "$CURRENT_PHASE"
        printf 'compose_project=%s\n' "$COMPOSE_PROJECT"
        printf 'live_provider_calls=disabled\n'
    } >"$ARTIFACT_DIR/summary.txt"

    rm -rf "$RUN_DIR"

    exit "$status"
}

trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Persons Finder verification\n'
printf 'Workspace: repository root\n'

CURRENT_PHASE="toolchain checks"
require_command java
require_command docker
require_command curl
require_command jq
require_command node
require_command openssl

NODE_MAJOR_VERSION="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
[[ "$NODE_MAJOR_VERSION" =~ ^[0-9]+$ && "$NODE_MAJOR_VERSION" -ge 18 ]] ||
    fail "Node.js 18 or newer is required; found $(node --version)"

JAVA_SPECIFICATION_VERSION="$(
    java -XshowSettings:properties -version 2>&1 |
        sed -n 's/^[[:space:]]*java\.specification\.version = //p'
)"
[[ "$JAVA_SPECIFICATION_VERSION" == "17" ]] ||
    fail "JDK 17 is required; found ${JAVA_SPECIFICATION_VERSION:-unknown}"

docker info >/dev/null
docker compose version
./gradlew --version

CURRENT_PHASE="focused bio adapter and conformance tests"
printf '\nRunning focused credential-free bio adapter checks\n'
./gradlew test \
    --no-daemon \
    --console=plain \
    --tests 'com.persons.finder.person.bio.BioConfigurationTest' \
    --tests 'com.persons.finder.person.bio.BioGeneratorConformanceTest' \
    --tests 'com.persons.finder.person.bio.BioPrivacyBoundaryTest' \
    --tests 'com.persons.finder.person.bio.remote.LiveAiTestAuthorizationTest' \
    --tests 'com.persons.finder.person.bio.remote.ModelProviderClientTest' \
    --tests 'com.persons.finder.person.bio.remote.ProviderHttpTransportTest' \
    --tests 'com.persons.finder.person.bio.remote.RemoteAdapterHostileStubIntegrationTest' \
    --tests 'com.persons.finder.person.bio.remote.RemoteBioGeneratorTest' \
    --tests 'com.persons.finder.person.bio.remote.eval.*'

CURRENT_PHASE="complete Gradle suite"
printf '\nRunning the complete clean build\n'
./gradlew clean build --no-daemon --console=plain

CURRENT_PHASE="isolated Compose setup"
mkdir -p "$ARTIFACT_DIR"

DATABASE_PASSWORD="$(openssl rand -hex 32)"
printf '%s\n' "$DATABASE_PASSWORD" >"$DATABASE_PASSWORD_FILE"
chmod 0444 "$DATABASE_PASSWORD_FILE"
printf '%s\n' \
    "$DATABASE_PASSWORD" \
    "$ROOT_DIR" \
    "$RUN_DIR" \
    "Verification Person" \
    "Software engineer" \
    "hiking" >"$FORBIDDEN_LOG_VALUES"

export VERIFY_APP_IMAGE="$COMPOSE_PROJECT-app:local"
export VERIFY_POSTGIS_IMAGE="$COMPOSE_PROJECT-postgis:17"
export VERIFY_DATABASE_PASSWORD_FILE="$DATABASE_PASSWORD_RELATIVE_FILE"
export PERSONS_FINDER_PORT=0

cat >"$COMPOSE_OVERRIDE" <<'YAML'
services:
  database:
    image: ${VERIFY_POSTGIS_IMAGE}
  app:
    image: ${VERIFY_APP_IMAGE}
    environment:
      PERSONS_BIO_GENERATOR: deterministic
      PERSONS_RUNTIME_MODE: assessment-local
secrets:
  database_password:
    file: ${VERIFY_DATABASE_PASSWORD_FILE}
YAML

compose config --quiet
COMPOSE_CONFIGURED=1
compose config --no-path-resolution >"$ARTIFACT_DIR/compose-config.yaml"

if grep -F -f "$FORBIDDEN_LOG_VALUES" "$ARTIFACT_DIR/compose-config.yaml" >/dev/null; then
    fail "resolved Compose configuration exposed a secret or synthetic profile value"
fi

CURRENT_PHASE="clean Compose build and startup"
printf '\nBuilding and starting an isolated Compose project\n'
compose build
compose up --detach --wait

APP_BINDING="$(compose port app 8080)"
[[ "$APP_BINDING" == 127.0.0.1:* ]] ||
    fail "app is not published on IPv4 loopback only: $APP_BINDING"
APP_PORT="${APP_BINDING##*:}"
[[ "$APP_PORT" =~ ^[0-9]+$ ]] || fail "could not resolve the ephemeral app port"
BASE_URL="http://127.0.0.1:$APP_PORT"

DATABASE_CONTAINER_ID="$(compose ps --quiet database)"
[[ -n "$DATABASE_CONTAINER_ID" ]] || fail "could not resolve the database container"
docker inspect \
    --format '{{json .NetworkSettings.Ports}}' \
    "$DATABASE_CONTAINER_ID" \
    >"$ARTIFACT_DIR/database-port-bindings.json"
jq -e \
    'type == "object" and all(to_entries[]; .value == null)' \
    "$ARTIFACT_DIR/database-port-bindings.json" >/dev/null ||
    fail "database unexpectedly published a host port"

CURRENT_PHASE="HTTP smoke"
curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/actuator/health/readiness" \
    >"$ARTIFACT_DIR/readiness-before-restart.json"
jq -e '.status == "UP"' "$ARTIFACT_DIR/readiness-before-restart.json" >/dev/null

curl \
    --fail-with-body \
    --silent \
    --show-error \
    --dump-header "$ARTIFACT_DIR/create-person.headers" \
    --output "$ARTIFACT_DIR/create-person.json" \
    --request POST \
    "$BASE_URL/persons" \
    --header 'Content-Type: application/json' \
    --data '{"name":"Verification Person","jobTitle":"Software engineer","hobbies":["hiking"],"location":{"latitude":-41.2865,"longitude":174.7762}}'

PERSON_ID="$(jq -er '.id' "$ARTIFACT_DIR/create-person.json")"
jq -e \
    --arg id "$PERSON_ID" \
    '
        .id == $id and
        .name == "Verification Person" and
        .jobTitle == "Software engineer" and
        .hobbies == ["hiking"] and
        (.bio | type == "string") and
        (.createdAt | type == "string") and
        (.lastKnownLocationAt | type == "string") and
        (has("location") | not)
    ' \
    "$ARTIFACT_DIR/create-person.json" >/dev/null
grep -F "Location: /persons/$PERSON_ID" "$ARTIFACT_DIR/create-person.headers" >/dev/null ||
    fail "create response did not contain the expected Location header"

curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/persons/nearby?lat=-41.2865&lon=174.7762&radius=1" \
    >"$ARTIFACT_DIR/nearby-before-move.json"
jq -e \
    --arg id "$PERSON_ID" \
    'type == "array" and length == 1 and .[0].id == $id and (.[0].distanceKm | type == "number")' \
    "$ARTIFACT_DIR/nearby-before-move.json" >/dev/null

curl \
    --fail-with-body \
    --silent \
    --show-error \
    --output "$ARTIFACT_DIR/update-location.json" \
    --request PUT \
    "$BASE_URL/persons/$PERSON_ID/location" \
    --header 'Content-Type: application/json' \
    --data '{"latitude":-36.8485,"longitude":174.7633}'
jq -e \
    --arg id "$PERSON_ID" \
    '
        .personId == $id and
        (.observationId | type == "string") and
        .lastKnownObservationId == .observationId and
        (.lastKnownLocationAt | type == "string")
    ' \
    "$ARTIFACT_DIR/update-location.json" >/dev/null

curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/persons/nearby?lat=-41.2865&lon=174.7762&radius=1" \
    >"$ARTIFACT_DIR/nearby-old-radius.json"
jq -e 'type == "array" and length == 0' "$ARTIFACT_DIR/nearby-old-radius.json" >/dev/null

curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/persons/nearby?lat=-36.8485&lon=174.7633&radius=1" \
    >"$ARTIFACT_DIR/nearby-new-radius.json"
jq -e \
    --arg id "$PERSON_ID" \
    'type == "array" and length == 1 and .[0].id == $id' \
    "$ARTIFACT_DIR/nearby-new-radius.json" >/dev/null

CURRENT_PHASE="database migration inspection"
CLUSTER_ID_BEFORE="$(
    compose exec -T database \
        psql \
        --username persons_finder \
        --dbname persons_finder \
        --tuples-only \
        --no-align \
        --set ON_ERROR_STOP=1 \
        --command='SELECT system_identifier FROM pg_control_system();'
)"
MIGRATION_COUNT="$(
    compose exec -T database \
        psql \
        --username persons_finder \
        --dbname persons_finder \
        --tuples-only \
        --no-align \
        --set ON_ERROR_STOP=1 \
        --command='SELECT count(*) FROM flyway_schema_history WHERE success;'
)"
[[ "$MIGRATION_COUNT" == "3" ]] ||
    fail "expected three successful Flyway migrations; found $MIGRATION_COUNT"

compose exec -T database \
    psql \
    --username persons_finder \
    --dbname persons_finder \
    --set ON_ERROR_STOP=1 \
    --command='SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;' \
    --command='SELECT PostGIS_Full_Version();' \
    >"$ARTIFACT_DIR/database-before-restart.txt"

CURRENT_PHASE="retained-volume restart"
printf '\nRestarting against the same isolated named volume\n'
compose restart
compose up --detach --wait

APP_BINDING="$(compose port app 8080)"
[[ "$APP_BINDING" == 127.0.0.1:* ]] ||
    fail "app is not published on IPv4 loopback only after restart: $APP_BINDING"
APP_PORT="${APP_BINDING##*:}"
BASE_URL="http://127.0.0.1:$APP_PORT"

curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/actuator/health/readiness" \
    >"$ARTIFACT_DIR/readiness-after-restart.json"
jq -e '.status == "UP"' "$ARTIFACT_DIR/readiness-after-restart.json" >/dev/null

curl \
    --fail-with-body \
    --silent \
    --show-error \
    "$BASE_URL/persons/nearby?lat=-36.8485&lon=174.7633&radius=1" \
    >"$ARTIFACT_DIR/nearby-after-restart.json"
jq -e \
    --arg id "$PERSON_ID" \
    'type == "array" and length == 1 and .[0].id == $id' \
    "$ARTIFACT_DIR/nearby-after-restart.json" >/dev/null

CLUSTER_ID_AFTER="$(
    compose exec -T database \
        psql \
        --username persons_finder \
        --dbname persons_finder \
        --tuples-only \
        --no-align \
        --set ON_ERROR_STOP=1 \
        --command='SELECT system_identifier FROM pg_control_system();'
)"
[[ "$CLUSTER_ID_AFTER" == "$CLUSTER_ID_BEFORE" ]] ||
    fail "database cluster identity changed across restart"

compose exec -T database \
    psql \
    --username persons_finder \
    --dbname persons_finder \
    --tuples-only \
    --no-align \
    --set ON_ERROR_STOP=1 \
    --command="SELECT 'persons=' || count(*) FROM person;" \
    --command="SELECT 'observations=' || count(*) FROM location_observation;" \
    --command="SELECT 'projections=' || count(*) FROM last_known_location_projection;" \
    >"$ARTIFACT_DIR/database-after-restart.txt"

grep -Fx 'persons=1' "$ARTIFACT_DIR/database-after-restart.txt" >/dev/null
grep -Fx 'observations=2' "$ARTIFACT_DIR/database-after-restart.txt" >/dev/null
grep -Fx 'projections=1' "$ARTIFACT_DIR/database-after-restart.txt" >/dev/null

CURRENT_PHASE="sensitive log inspection"
capture_compose_evidence
if grep -F -f "$FORBIDDEN_LOG_VALUES" "$ARTIFACT_DIR/compose.log" >/dev/null; then
    fail "application or database logs exposed a secret or synthetic profile value"
fi

CURRENT_PHASE="complete"
printf '\nVerification passed\n'
