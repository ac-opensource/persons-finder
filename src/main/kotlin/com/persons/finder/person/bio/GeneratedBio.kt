package com.persons.finder.person.bio

import com.persons.finder.person.model.PersonProfile
import java.text.Normalizer
import java.util.Locale

/**
 * Application-owned validated prose template. A remote model may author the
 * candidate text, but only this deterministic validator can construct the type.
 */
@JvmInline
value class GeneratedBioTemplate private constructor(val value: String) {
    companion object {
        fun validate(candidate: String): BioGenerationResult =
            validateWithDiagnostic(candidate).result

        internal fun validateWithDiagnostic(
            candidate: String,
        ): DiagnosedBioTemplateValidation {
            val normalized =
                if (candidate.isWellFormedUtf16()) {
                    Normalizer.normalize(candidate.trimUnicodeWhitespace(), Normalizer.Form.NFC)
                } else {
                    return DiagnosedBioTemplateValidation.rejected(
                        GeneratedBioTemplateRejectionReason.MALFORMED_UNICODE,
                    )
                }
            val rejection =
                when {
                    normalized.isEmpty() -> GeneratedBioTemplateRejectionReason.EMPTY
                    normalized.codePointCount(0, normalized.length) > MAX_TEMPLATE_CODE_POINTS ->
                        GeneratedBioTemplateRejectionReason.TOTAL_CODE_POINT_LIMIT

                    normalized.codePoints().anyMatch(::isForbiddenTemplateCodePoint) ->
                        GeneratedBioTemplateRejectionReason.FORBIDDEN_CODE_POINT

                    !TEMPLATE_CHARACTERS.matches(normalized) ->
                        GeneratedBioTemplateRejectionReason.CHARACTER_POLICY

                    REQUIRED_TOKENS.any { token -> normalized.literalCount(token) != 1 } ->
                        GeneratedBioTemplateRejectionReason.PLACEHOLDER_CARDINALITY

                    hasUnknownOrMutatedPlaceholder(normalized) ->
                        GeneratedBioTemplateRejectionReason.UNKNOWN_OR_MUTATED_PLACEHOLDER

                    hasQuotedOrMarkupWrappedPlaceholder(normalized) ->
                        GeneratedBioTemplateRejectionReason.WRAPPED_PLACEHOLDER

                    DISALLOWED_REGION_TERMS.any { normalized.contains(it, ignoreCase = true) } ->
                        GeneratedBioTemplateRejectionReason.FORBIDDEN_REGION

                    violatesGeneratedBioContentPolicy(normalized) ->
                        GeneratedBioTemplateRejectionReason.CONTENT_POLICY

                    normalized.templateLiteralCodePointCount() >
                        BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS ->
                        GeneratedBioTemplateRejectionReason.LITERAL_CODE_POINT_LIMIT

                    !normalized.hasSafeSentenceCount() ->
                        GeneratedBioTemplateRejectionReason.SENTENCE_COUNT

                    else -> null
                }
            return if (rejection == null) {
                DiagnosedBioTemplateValidation(
                    result = BioGenerationResult.Template(GeneratedBioTemplate(normalized)),
                    rejectionReason = null,
                )
            } else {
                DiagnosedBioTemplateValidation.rejected(rejection)
            }
        }

        internal fun fromCatalog(templateId: BioTemplateId): GeneratedBioTemplate {
            val candidate =
                when (templateId) {
                    BioTemplateId.QUIRKY_SIDE_QUEST ->
                        "Meet {{NAME}}, a very quirky {{JOB}} who enjoys {{HOBBY}}."

                    BioTemplateId.DELIGHTFUL_TWIST ->
                        "{{NAME}} is one quirky {{JOB}} with a knack for {{HOBBY}}."

                    BioTemplateId.CURIOUS_ADVENTURE ->
                        "Quirky {{JOB}} {{NAME}} makes {{HOBBY}} a daily adventure."
                }
            return when (val result = validate(candidate)) {
                is BioGenerationResult.Template -> result.value
                is BioGenerationResult.Failure ->
                    error("Application-owned template catalog is invalid: ${result.reason}")
            }
        }
    }
}

internal data class DiagnosedBioTemplateValidation(
    val result: BioGenerationResult,
    val rejectionReason: GeneratedBioTemplateRejectionReason?,
) {
    companion object {
        fun rejected(
            reason: GeneratedBioTemplateRejectionReason,
        ): DiagnosedBioTemplateValidation =
            DiagnosedBioTemplateValidation(
                result = BioGenerationResult.Failure(reason.failure),
                rejectionReason = reason,
            )
    }
}

internal enum class GeneratedBioTemplateRejectionReason(
    val wireValue: String,
    val failure: BioGenerationFailure = BioGenerationFailure.INVALID_OUTPUT,
) {
    MALFORMED_UNICODE("malformed_unicode"),
    EMPTY("empty"),
    TOTAL_CODE_POINT_LIMIT("total_code_point_limit"),
    FORBIDDEN_CODE_POINT("forbidden_code_point"),
    CHARACTER_POLICY("character_policy"),
    PLACEHOLDER_CARDINALITY("placeholder_cardinality"),
    UNKNOWN_OR_MUTATED_PLACEHOLDER("unknown_or_mutated_placeholder"),
    WRAPPED_PLACEHOLDER("wrapped_placeholder"),
    FORBIDDEN_REGION("forbidden_region", BioGenerationFailure.POLICY_REJECTED),
    CONTENT_POLICY("content_policy", BioGenerationFailure.POLICY_REJECTED),
    LITERAL_CODE_POINT_LIMIT("literal_code_point_limit"),
    SENTENCE_COUNT("sentence_count"),
}

internal data class ObservedBioTemplateMetrics(
    val wellFormedUnicode: Boolean,
    val codePoints: Int?,
    val modelAuthoredCodePoints: Int?,
    val namePlaceholderCount: Int,
    val jobPlaceholderCount: Int,
    val hobbyPlaceholderCount: Int,
    val sentenceCount: Int?,
    val printableAscii: Boolean?,
)

internal fun observeBioTemplate(candidate: String): ObservedBioTemplateMetrics {
    if (!candidate.isWellFormedUtf16()) {
        return ObservedBioTemplateMetrics(
            wellFormedUnicode = false,
            codePoints = null,
            modelAuthoredCodePoints = null,
            namePlaceholderCount = candidate.literalCount(TemplateToken.NAME.literal),
            jobPlaceholderCount = candidate.literalCount(TemplateToken.JOB.literal),
            hobbyPlaceholderCount = candidate.literalCount(TemplateToken.HOBBY.literal),
            sentenceCount = null,
            printableAscii = null,
        )
    }
    val normalized =
        Normalizer.normalize(candidate.trimUnicodeWhitespace(), Normalizer.Form.NFC)
    return ObservedBioTemplateMetrics(
        wellFormedUnicode = true,
        codePoints = normalized.codePointCount(0, normalized.length),
        modelAuthoredCodePoints = normalized.templateLiteralCodePointCount(),
        namePlaceholderCount = normalized.literalCount(TemplateToken.NAME.literal),
        jobPlaceholderCount = normalized.literalCount(TemplateToken.JOB.literal),
        hobbyPlaceholderCount = normalized.literalCount(TemplateToken.HOBBY.literal),
        sentenceCount = normalized.observedSentenceCount(),
        printableAscii = normalized.codePoints().allMatch { it in PRINTABLE_ASCII_CODE_POINTS },
    )
}

internal fun GeneratedBioTemplate.modelAuthoredCodePointCount(): Int =
    value.templateLiteralCodePointCount()

internal fun GeneratedBioTemplate.sentenceCount(): Int =
    recognizedInternalSentenceBoundaryCount(value.dropLast(1)) + 1

/**
 * Final write-boundary type. Construction parses template tokens, appends
 * source values as opaque segments once, and independently checks grounding
 * and the final public contract.
 */
@JvmInline
value class GeneratedBio private constructor(val value: String) {
    companion object {
        internal fun compose(
            template: GeneratedBioTemplate,
            profile: PersonProfile,
            selectedHobby: String,
        ): GeneratedBio {
            require(selectedHobby in profile.hobbies) {
                "Grounding hobby must be one of the validated profile hobbies"
            }
            return compose(
                template,
                BioGrounding(
                    name = profile.name,
                    jobTitle = profile.jobTitle,
                    hobby = selectedHobby,
                ),
            )
        }

        internal fun compose(
            template: GeneratedBioTemplate,
            grounding: BioGrounding,
        ): GeneratedBio {
            val segments = parseTemplate(template.value)
            val rendered = StringBuilder()
            val groundedTokens = mutableListOf<TemplateToken>()
            segments.forEach { segment ->
                when (segment) {
                    is TemplateSegment.Literal -> rendered.append(segment.value)
                    is TemplateSegment.Placeholder -> {
                        rendered.append(grounding.valueFor(segment.token))
                        groundedTokens += segment.token
                    }
                }
            }

            require(
                groundedTokens.size == REQUIRED_TEMPLATE_TOKEN_ORDER.size &&
                    groundedTokens.toSet() == REQUIRED_TEMPLATE_TOKEN_ORDER.toSet() &&
                    REQUIRED_TEMPLATE_TOKEN_ORDER.all { token -> groundedTokens.count { it == token } == 1 },
            ) {
                "Generated bio grounding did not consume each required placeholder exactly once"
            }
            val bio = rendered.toString()
            require(bio.isWellFormedUtf16()) { "Composed bio contains malformed Unicode" }
            require(bio.codePoints().noneMatch(::isForbiddenFinalCodePoint)) {
                "Composed bio contains forbidden controls"
            }
            require(bio.hasSafeSentenceCount()) {
                "Composed bio must contain between one and three safe sentences"
            }
            require(bio.codePointCount(0, bio.length) <= BioPolicy.FINAL_BIO_MAX_CODE_POINTS) {
                "Composed bio exceeds its final limit"
            }
            return GeneratedBio(bio)
        }
    }
}

internal data class BioGrounding(
    val name: String,
    val jobTitle: String,
    val hobby: String,
) {
    internal fun valueFor(token: TemplateToken): String =
        when (token) {
            TemplateToken.NAME -> name
            TemplateToken.JOB -> jobTitle
            TemplateToken.HOBBY -> hobby
        }
}

internal enum class TemplateToken(val literal: String) {
    NAME("{{NAME}}"),
    JOB("{{JOB}}"),
    HOBBY("{{HOBBY}}"),
}

private sealed interface TemplateSegment {
    data class Literal(val value: String) : TemplateSegment

    data class Placeholder(val token: TemplateToken) : TemplateSegment
}

private fun parseTemplate(value: String): List<TemplateSegment> {
    val segments = mutableListOf<TemplateSegment>()
    var cursor = 0
    APPROVED_TOKEN.findAll(value).forEach { match ->
        if (match.range.first > cursor) {
            segments += TemplateSegment.Literal(value.substring(cursor, match.range.first))
        }
        segments += TemplateSegment.Placeholder(TemplateToken.valueOf(match.groupValues[1]))
        cursor = match.range.last + 1
    }
    if (cursor < value.length) {
        segments += TemplateSegment.Literal(value.substring(cursor))
    }
    require(
        segments.filterIsInstance<TemplateSegment.Placeholder>().map { it.token }.toSet() ==
            REQUIRED_TEMPLATE_TOKEN_ORDER.toSet(),
    ) {
        "Generated template does not contain the complete placeholder set"
    }
    return segments
}

private fun hasUnknownOrMutatedPlaceholder(value: String): Boolean {
    val remainder =
        REQUIRED_TOKENS.fold(value) { current, token ->
            current.replace(token, "")
        }
    return remainder.any { it == '{' || it == '}' }
}

private fun hasQuotedOrMarkupWrappedPlaceholder(value: String): Boolean =
    REQUIRED_TOKENS.any { token ->
        val index = value.indexOf(token)
        if (index < 0) {
            false
        } else {
            val before = value.substring(0, index).trimEnd().lastOrNull()
            val after = value.substring(index + token.length).trimStart().firstOrNull()
            before in TOKEN_WRAPPERS || after in TOKEN_WRAPPERS
        }
    }

private fun String.hasSafeSentenceCount(): Boolean {
    if (isBlank()) {
        return false
    }
    val terminal = lastOrNull() ?: return false
    if (terminal !in SENTENCE_TERMINATORS) {
        return false
    }
    val internalSentenceBoundaries = recognizedInternalSentenceBoundaryCount(dropLast(1))
    return internalSentenceBoundaries + 1 <= MAX_BIO_SENTENCES
}

private fun String.observedSentenceCount(): Int? {
    val terminal = lastOrNull() ?: return null
    if (terminal !in SENTENCE_TERMINATORS) {
        return null
    }
    return recognizedInternalSentenceBoundaryCount(dropLast(1)) + 1
}

private fun recognizedInternalSentenceBoundaryCount(value: String): Int =
    SENTENCE_BOUNDARY_WITH_CONTINUATION.findAll(value).count { boundary ->
        boundary.groupValues[1] != "." || boundary.isTerminalPeriod(value)
    }

private fun MatchResult.isTerminalPeriod(value: String): Boolean {
    val precedingWord =
        PRECEDING_WORD
            .find(value.substring(0, range.first))
            ?.value
            ?.lowercase(Locale.ROOT)
    return precedingWord == null || precedingWord !in NON_TERMINAL_PERIOD_WORDS
}

private fun String.templateLiteralCodePointCount(): Int =
    REQUIRED_TOKENS
        .fold(this) { current, token -> current.replace(token, "") }
        .let { literal -> literal.codePointCount(0, literal.length) }

private fun violatesGeneratedBioContentPolicy(value: String): Boolean =
    violatesBioContentPolicy(value) ||
        FORBIDDEN_GENERATED_PATTERNS.any { it.containsMatchIn(value) }

private fun String.literalCount(value: String): Int =
    value.toRegex(RegexOption.LITERAL).findAll(this).count()

private fun String.trimUnicodeWhitespace(): String {
    var start = 0
    var end = length
    while (start < end) {
        val codePoint = codePointAt(start)
        if (!codePoint.isUnicodeWhitespace()) break
        start += Character.charCount(codePoint)
    }
    while (end > start) {
        val codePoint = codePointBefore(end)
        if (!codePoint.isUnicodeWhitespace()) break
        end -= Character.charCount(codePoint)
    }
    return substring(start, end)
}

private fun String.isWellFormedUtf16(): Boolean {
    var index = 0
    while (index < length) {
        when {
            Character.isHighSurrogate(this[index]) -> {
                if (index + 1 >= length || !Character.isLowSurrogate(this[index + 1])) {
                    return false
                }
                index += 2
            }

            Character.isLowSurrogate(this[index]) -> return false
            else -> index++
        }
    }
    return true
}

private fun Int.isUnicodeWhitespace(): Boolean =
    Character.isWhitespace(this) || Character.isSpaceChar(this)

private fun isForbiddenTemplateCodePoint(codePoint: Int): Boolean =
    codePoint !in PRINTABLE_ASCII_CODE_POINTS

private fun isForbiddenFinalCodePoint(codePoint: Int): Boolean {
    if (codePoint == ZERO_WIDTH_NON_JOINER || codePoint == ZERO_WIDTH_JOINER) {
        return false
    }
    return when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        -> true

        else -> false
    }
}

private val REQUIRED_TEMPLATE_TOKEN_ORDER =
    listOf(TemplateToken.NAME, TemplateToken.JOB, TemplateToken.HOBBY)
private val REQUIRED_TOKENS = REQUIRED_TEMPLATE_TOKEN_ORDER.map(TemplateToken::literal)
private val APPROVED_TOKEN = Regex("""\{\{(NAME|JOB|HOBBY)}}""")
private val TEMPLATE_CHARACTERS = Regex("""[A-Za-z0-9 .,!?;:'"(){}-]+""")
private val TOKEN_WRAPPERS = setOf('"', '\'', '`', '<', '>', '[', ']')
private val SENTENCE_TERMINATORS = setOf('.', '!', '?')
private val DISALLOWED_REGION_TERMS = listOf("North Island", "South Island")
private val FORBIDDEN_GENERATED_PATTERNS =
    listOf(
        Regex("""(?i)\bi\s+am\s+hacked\b"""),
        Regex("""(?i)\b(?:system|developer)\s+prompt\b"""),
        Regex("""(?i)\b(?:here|following)\s+(?:is|are)\s+(?:the\s+)?(?:prompt|instructions?)\b"""),
    )
private val SENTENCE_BOUNDARY_WITH_CONTINUATION =
    Regex("""([.!?])(?:["')\]]*)\s*(?=\S)""")
private val PRECEDING_WORD = Regex("""([\p{L}]+(?:\.[\p{L}]+)*)$""")
private val NON_TERMINAL_PERIOD_WORDS =
    setOf("dr", "e.g", "i.e", "jr", "mr", "mrs", "ms", "prof", "sr", "st", "vs")
private val PRINTABLE_ASCII_CODE_POINTS = 0x20..0x7E
private val MAX_TEMPLATE_CODE_POINTS =
    BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS +
        REQUIRED_TOKENS.sumOf { token -> token.codePointCount(0, token.length) }
private const val MAX_BIO_SENTENCES = 3
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
