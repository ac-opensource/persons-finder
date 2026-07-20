package com.persons.finder.web

import com.fasterxml.jackson.annotation.JsonInclude
import java.net.URI

class ApiProblem(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String,
    val code: ProblemCode,
    violations: List<ProblemViolation> = emptyList(),
) {
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val violations: List<ProblemViolation> = violations.toList()

    init {
        require(type == code.type && status == code.status) {
            "Problem type, status, and code must describe the same error"
        }
        require(
            if (code == ProblemCode.VALIDATION_FAILED) {
                violations.isNotEmpty()
            } else {
                violations.isEmpty()
            },
        ) {
            "Only validation problems contain violations"
        }
        require(violations == violations.sortedWith(PROBLEM_VIOLATION_ORDER)) {
            "Problem violations must be sorted by field and code"
        }
    }
}

data class ProblemViolation(
    val field: String,
    val code: ViolationCode,
)

enum class ProblemCode(
    val status: Int,
    val type: URI,
) {
    VALIDATION_FAILED(
        status = 400,
        type = URI.create("urn:persons-finder:problem:validation-failed"),
    ),
    PERSON_NOT_FOUND(
        status = 404,
        type = URI.create("urn:persons-finder:problem:person-not-found"),
    ),
    IDEMPOTENCY_KEY_REUSED(
        status = 409,
        type = URI.create("urn:persons-finder:problem:idempotency-key-reused"),
    ),
    BIO_INPUT_REJECTED(
        status = 422,
        type = URI.create("urn:persons-finder:problem:bio-input-rejected"),
    ),
    BIO_GENERATION_UNAVAILABLE(
        status = 503,
        type = URI.create("urn:persons-finder:problem:bio-generation-unavailable"),
    ),
}

enum class ViolationCode {
    REQUIRED,
    INVALID_TYPE,
    INVALID_FORMAT,
    UNKNOWN_FIELD,
    OUT_OF_RANGE,
    TOO_LONG,
    TOO_MANY_ITEMS,
    DUPLICATE_ITEM,
}

private val PROBLEM_VIOLATION_ORDER =
    compareBy(ProblemViolation::field, { it.code.name })
