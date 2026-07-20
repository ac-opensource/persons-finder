package com.persons.finder.person.bio

import com.persons.finder.person.model.PersonProfile
import java.text.Normalizer
import java.util.Locale

/**
 * Application-owned validated template. Provider responses never construct
 * this type directly: a remote adapter can only select a closed catalog ID,
 * which is resolved and revalidated inside the application boundary.
 */
@JvmInline
value class GeneratedBioTemplate private constructor(val value: String) {
    companion object {
        fun validate(candidate: String): BioGenerationResult {
            val normalized =
                if (candidate.isWellFormedUtf16()) {
                    Normalizer.normalize(candidate.trimUnicodeWhitespace(), Normalizer.Form.NFC)
                } else {
                    return BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
                }
            val failure =
                when {
                    normalized.isEmpty() -> BioGenerationFailure.INVALID_OUTPUT
                    normalized.codePointCount(0, normalized.length) > MAX_TEMPLATE_CODE_POINTS ->
                        BioGenerationFailure.INVALID_OUTPUT
                    normalized.codePoints().anyMatch(::isForbiddenTemplateCodePoint) ->
                        BioGenerationFailure.INVALID_OUTPUT
                    !TEMPLATE_CHARACTERS.matches(normalized) -> BioGenerationFailure.INVALID_OUTPUT
                    REQUIRED_TOKENS.any { token -> normalized.literalCount(token) != 1 } ->
                        BioGenerationFailure.INVALID_OUTPUT
                    hasUnknownOrMutatedPlaceholder(normalized) -> BioGenerationFailure.INVALID_OUTPUT
                    hasQuotedOrMarkupWrappedPlaceholder(normalized) -> BioGenerationFailure.INVALID_OUTPUT
                    DISALLOWED_REGION_TERMS.any { normalized.contains(it, ignoreCase = true) } ->
                        BioGenerationFailure.POLICY_REJECTED
                    violatesGeneratedBioContentPolicy(normalized) ->
                        BioGenerationFailure.POLICY_REJECTED
                    !normalized.isOneSafeSentence() -> BioGenerationFailure.INVALID_OUTPUT
                    else -> null
                }
            return if (failure == null) {
                BioGenerationResult.Template(GeneratedBioTemplate(normalized))
            } else {
                BioGenerationResult.Failure(failure)
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

private fun String.isOneSafeSentence(): Boolean {
    if (isBlank()) {
        return false
    }
    val terminal = lastOrNull() ?: return false
    if (terminal !in SENTENCE_TERMINATORS) {
        return false
    }
    return !hasUnrecognizedInternalSentenceBoundary(dropLast(1))
}

private fun hasUnrecognizedInternalSentenceBoundary(value: String): Boolean =
    SENTENCE_BOUNDARY_WITH_CONTINUATION.findAll(value).any { boundary ->
        if (boundary.groupValues[1] != ".") {
            true
        } else {
            val precedingWord =
                PRECEDING_WORD
                    .find(value.substring(0, boundary.range.first))
                    ?.value
                    ?.lowercase(Locale.ROOT)
            precedingWord == null ||
                (precedingWord.length > 1 && precedingWord !in NON_TERMINAL_PERIOD_WORDS)
        }
    }

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
private const val MAX_TEMPLATE_CODE_POINTS = 240
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
