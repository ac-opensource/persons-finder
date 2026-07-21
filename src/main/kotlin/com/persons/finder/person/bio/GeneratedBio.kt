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
        fun validate(
            candidate: String,
            hobbyCount: Int = 1,
        ): BioGenerationResult = validateWithDiagnostic(candidate, hobbyCount).result

        internal fun validateWithDiagnostic(
            candidate: String,
            hobbyCount: Int = 1,
        ): DiagnosedBioTemplateValidation {
            val requiredTokens = requiredTemplateTokens(hobbyCount)
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
                    normalized.codePointCount(0, normalized.length) >
                        maxTemplateCodePoints(requiredTokens) ->
                        GeneratedBioTemplateRejectionReason.TOTAL_CODE_POINT_LIMIT

                    normalized.codePoints().anyMatch(::isForbiddenTemplateCodePoint) ->
                        GeneratedBioTemplateRejectionReason.FORBIDDEN_CODE_POINT

                    !TEMPLATE_CHARACTERS.matches(normalized) ->
                        GeneratedBioTemplateRejectionReason.CHARACTER_POLICY

                    !normalized.hasExactPlaceholderSet(requiredTokens) ->
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

        internal fun fromCatalog(
            templateId: BioTemplateId,
            hobbyCount: Int = 1,
        ): GeneratedBioTemplate {
            val hobbyStory = catalogHobbyStory(templateId, hobbyCount)
            val candidate =
                when (templateId) {
                    BioTemplateId.QUIRKY_SIDE_QUEST ->
                        "Meet {{NAME}}, a very quirky {{JOB}}: $hobbyStory."

                    BioTemplateId.DELIGHTFUL_TWIST ->
                        "{{NAME}} is one quirky {{JOB}}: $hobbyStory."

                    BioTemplateId.CURIOUS_ADVENTURE ->
                        "Quirky {{JOB}} {{NAME}}: $hobbyStory."
                }
            return when (val result = validate(candidate, hobbyCount)) {
                is BioGenerationResult.Template -> result.value
                is BioGenerationResult.Failure ->
                    error("Application-owned template catalog is invalid: ${result.reason}")
            }
        }

        private fun catalogHobbyStory(
            templateId: BioTemplateId,
            hobbyCount: Int,
        ): String {
            val beats =
                when (templateId) {
                    BioTemplateId.QUIRKY_SIDE_QUEST ->
                        listOf("opens the side quest", "adds the plot twist", "powers the finale")

                    BioTemplateId.DELIGHTFUL_TWIST ->
                        listOf("keeps ideas bright", "brings delightful chaos", "earns an encore")

                    BioTemplateId.CURIOUS_ADVENTURE ->
                        listOf("sparks curiosity", "fuels the next detour", "makes the day an adventure")
                }
            return hobbyPlaceholders(hobbyCount)
                .mapIndexed { index, placeholder -> "$placeholder ${beats[index % beats.size]}" }
                .joinToString("; ")
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
            namePlaceholderCount = candidate.literalCount(TemplateToken.Name.literal),
            jobPlaceholderCount = candidate.literalCount(TemplateToken.Job.literal),
            hobbyPlaceholderCount = candidate.hobbyPlaceholderCount(),
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
        namePlaceholderCount = normalized.literalCount(TemplateToken.Name.literal),
        jobPlaceholderCount = normalized.literalCount(TemplateToken.Job.literal),
        hobbyPlaceholderCount = normalized.hobbyPlaceholderCount(),
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
 * and the final public contract. Sentence structure is established by the
 * validated template; punctuation in opaque source segments is not reparsed.
 */
@JvmInline
value class GeneratedBio private constructor(val value: String) {
    companion object {
        internal fun compose(
            template: GeneratedBioTemplate,
            profile: PersonProfile,
        ): GeneratedBio {
            return compose(
                template,
                BioGrounding(
                    name = profile.name,
                    jobTitle = profile.jobTitle,
                    hobbies = profile.hobbies,
                ),
            )
        }

        internal fun compose(
            template: GeneratedBioTemplate,
            grounding: BioGrounding,
        ): GeneratedBio {
            val requiredTokens = requiredTemplateTokens(grounding.hobbies.size)
            val segments = parseTemplate(template.value)
            val groundedTokens =
                segments.filterIsInstance<TemplateSegment.Placeholder>().map { it.token }
            require(
                groundedTokens.size == requiredTokens.size &&
                    groundedTokens.toSet() == requiredTokens.toSet() &&
                    requiredTokens.all { token -> groundedTokens.count { it == token } == 1 },
            ) {
                "Generated bio grounding did not consume each required placeholder exactly once"
            }
            val rendered = StringBuilder()
            segments.forEach { segment ->
                when (segment) {
                    is TemplateSegment.Literal -> rendered.append(segment.value)
                    is TemplateSegment.Placeholder -> {
                        rendered.append(grounding.valueFor(segment.token))
                    }
                }
            }
            val bio = rendered.toString()
            require(bio.isWellFormedUtf16()) { "Composed bio contains malformed Unicode" }
            require(bio.codePoints().noneMatch(::isForbiddenFinalCodePoint)) {
                "Composed bio contains forbidden controls"
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
    val hobbies: List<String>,
) {
    internal fun valueFor(token: TemplateToken): String =
        when (token) {
            TemplateToken.Name -> name
            TemplateToken.Job -> jobTitle
            is TemplateToken.Hobby -> hobbies[token.index]
        }
}

internal sealed interface TemplateToken {
    val literal: String

    data object Name : TemplateToken {
        override val literal: String = "{{NAME}}"
    }

    data object Job : TemplateToken {
        override val literal: String = "{{JOB}}"
    }

    data class Hobby(val index: Int) : TemplateToken {
        init {
            require(index in 0 until BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS)
        }

        override val literal: String = hobbyPlaceholder(index)
    }
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
        segments += TemplateSegment.Placeholder(match.toTemplateToken())
        cursor = match.range.last + 1
    }
    if (cursor < value.length) {
        segments += TemplateSegment.Literal(value.substring(cursor))
    }
    return segments
}

private fun hasUnknownOrMutatedPlaceholder(value: String): Boolean {
    val remainder = APPROVED_TOKEN.replace(value, "")
    return remainder.any { it == '{' || it == '}' }
}

private fun String.hasExactPlaceholderSet(requiredTokens: List<TemplateToken>): Boolean {
    val observedTokens = APPROVED_TOKEN.findAll(this).map(MatchResult::toTemplateToken).toList()
    return observedTokens.size == requiredTokens.size &&
        observedTokens.toSet() == requiredTokens.toSet() &&
        requiredTokens.all { token -> observedTokens.count { it == token } == 1 }
}

private fun String.hobbyPlaceholderCount(): Int =
    APPROVED_TOKEN.findAll(this).count { match -> match.groupValues[1].startsWith("HOBBY[") }

private fun MatchResult.toTemplateToken(): TemplateToken =
    when (groupValues[1]) {
        "NAME" -> TemplateToken.Name
        "JOB" -> TemplateToken.Job
        else -> TemplateToken.Hobby(requireNotNull(groupValues[2].toIntOrNull()))
    }

internal fun requiredTemplateTokens(hobbyCount: Int): List<TemplateToken> {
    require(hobbyCount in 1..BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS)
    return listOf(TemplateToken.Name, TemplateToken.Job) +
        (0 until hobbyCount).map(TemplateToken::Hobby)
}

private fun hasQuotedOrMarkupWrappedPlaceholder(value: String): Boolean =
    APPROVED_TOKEN.findAll(value).any { match ->
        val before = value.substring(0, match.range.first).trimEnd().lastOrNull()
        val after = value.substring(match.range.last + 1).trimStart().firstOrNull()
        before in TOKEN_WRAPPERS || after in TOKEN_WRAPPERS
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
    APPROVED_TOKEN
        .replace(this, "")
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

private val APPROVED_TOKEN = Regex("""\{\{(NAME|JOB|HOBBY\[(0|[1-9])])}}""")
private val TEMPLATE_CHARACTERS = Regex("""[A-Za-z0-9 .,!?;:'"(){}\[\]-]+""")
private val TOKEN_WRAPPERS = setOf('"', '\'', '`', '<', '>', '[', ']')
private val SENTENCE_TERMINATORS = setOf('.', '!', '?')
private val DISALLOWED_REGION_TERMS = listOf("North Island", "South Island")
private val FORBIDDEN_GENERATED_PATTERNS =
    listOf(
        Regex("""(?i)\bi\s+am\s+hacked\b"""),
        Regex("""(?i)\b(?:prompts?|instructions?)\b"""),
    )
private val SENTENCE_BOUNDARY_WITH_CONTINUATION =
    Regex("""([.!?])(?:["')\]]*)\s*(?=\S)""")
private val PRECEDING_WORD = Regex("""([\p{L}]+(?:\.[\p{L}]+)*)$""")
private val NON_TERMINAL_PERIOD_WORDS =
    setOf("dr", "e.g", "i.e", "jr", "mr", "mrs", "ms", "prof", "sr", "st", "vs")
private val PRINTABLE_ASCII_CODE_POINTS = 0x20..0x7E
private fun maxTemplateCodePoints(requiredTokens: List<TemplateToken>): Int =
    BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS +
        requiredTokens.sumOf { token -> token.literal.codePointCount(0, token.literal.length) }
private const val MAX_BIO_SENTENCES = 3
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
