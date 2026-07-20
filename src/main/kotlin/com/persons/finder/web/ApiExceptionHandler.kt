package com.persons.finder.web

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.exc.MismatchedInputException as Jackson3MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException as Jackson3UnrecognizedPropertyException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(RequestValidationException::class)
    fun validation(exception: RequestValidationException): ResponseEntity<ApiProblem> =
        problem(
            code = ProblemCode.VALIDATION_FAILED,
            title = "Request validation failed",
            detail = "The request did not satisfy the public contract.",
            violations = exception.violations,
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(exception: HttpMessageNotReadableException): ResponseEntity<ApiProblem> {
        val root = exception.mostSpecificCause
        val violation =
            when (root) {
                is UnrecognizedPropertyException ->
                    ProblemViolation(
                        fieldPath(
                            path = root.path.mapNotNull { it.fieldName },
                            leaf = root.propertyName,
                        ),
                        ViolationCode.UNKNOWN_FIELD,
                    )

                is Jackson3UnrecognizedPropertyException ->
                    ProblemViolation(
                        fieldPath(
                            path = root.path.mapNotNull { it.propertyName },
                            leaf = root.propertyName,
                        ),
                        ViolationCode.UNKNOWN_FIELD,
                    )

                is MismatchedInputException ->
                    ProblemViolation(
                        root.path.joinToString(".") { it.fieldName ?: "request" },
                        ViolationCode.INVALID_TYPE,
                    )

                is Jackson3MismatchedInputException ->
                    ProblemViolation(
                        root.path
                            .mapNotNull { it.propertyName }
                            .joinToString(".")
                            .ifEmpty { "request" },
                        ViolationCode.INVALID_TYPE,
                    )

                else -> ProblemViolation("request", ViolationCode.INVALID_FORMAT)
            }
        return problem(
            code = ProblemCode.VALIDATION_FAILED,
            title = "Request validation failed",
            detail = "The request body could not be read.",
            violations = listOf(violation),
        )
    }

    @ExceptionHandler(PersonNotFoundApiException::class)
    fun personNotFound(): ResponseEntity<ApiProblem> =
        problem(
            ProblemCode.PERSON_NOT_FOUND,
            "Person not found",
            "The requested person does not exist.",
        )

    @ExceptionHandler(IdempotencyKeyReusedApiException::class)
    fun idempotencyConflict(): ResponseEntity<ApiProblem> =
        problem(
            ProblemCode.IDEMPOTENCY_KEY_REUSED,
            "Idempotency key reused",
            "The client update identifier was reused with different content.",
        )

    @ExceptionHandler(BioInputRejectedApiException::class)
    fun bioInputRejected(): ResponseEntity<ApiProblem> =
        problem(
            ProblemCode.BIO_INPUT_REJECTED,
            "Bio input rejected",
            "The bio source input was rejected by policy.",
        )

    @ExceptionHandler(BioGenerationUnavailableApiException::class)
    fun bioUnavailable(): ResponseEntity<ApiProblem> =
        problem(
            ProblemCode.BIO_GENERATION_UNAVAILABLE,
            "Bio generation unavailable",
            "A safe bio could not be generated.",
        )
}

class RequestValidationException(
    violations: List<ProblemViolation>,
) : RuntimeException() {
    val violations =
        violations
            .distinct()
            .sortedWith(compareBy(ProblemViolation::field, { it.code.name }))
}

class PersonNotFoundApiException : RuntimeException()

class IdempotencyKeyReusedApiException : RuntimeException()

class BioInputRejectedApiException : RuntimeException()

class BioGenerationUnavailableApiException : RuntimeException()

private fun fieldPath(
    path: List<String>,
    leaf: String,
): String =
    (path + leaf)
        .fold(emptyList<String>()) { fields, field ->
            if (fields.lastOrNull() == field) fields else fields + field
        }
        .joinToString(".")

private fun problem(
    code: ProblemCode,
    title: String,
    detail: String,
    violations: List<ProblemViolation> = emptyList(),
): ResponseEntity<ApiProblem> =
    ResponseEntity
        .status(HttpStatus.valueOf(code.status))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            ApiProblem(
                type = code.type,
                title = title,
                status = code.status,
                detail = detail,
                code = code,
                violations = violations,
            ),
        )
