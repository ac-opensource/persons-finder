package com.persons.finder.person.bio

import com.persons.finder.person.model.PersonProfile
import java.text.Normalizer
import java.util.Locale

class BioPolicy {
    fun prepare(profile: PersonProfile): PreparedBioRequest {
        val mappedHobbies =
            profile.hobbies.map { hobby ->
                MappedHobby(
                    original = hobby,
                    code =
                        ReviewedBioAliases.interests[hobby.aliasKey()]
                            ?: SafeInterestCode.OTHER,
                    hasReviewedAlias =
                        ReviewedBioAliases.interests.containsKey(hobby.aliasKey()),
                )
            }
        val selectedHobby =
            mappedHobbies.firstOrNull(MappedHobby::hasReviewedAlias)?.original
                ?: mappedHobbies.first().original
        requireCompositionFits(profile, selectedHobby)

        if (
            sourceValuesForInspection(profile).any(::violatesBioContentPolicy)
        ) {
            throw UnsafeBioInputException()
        }

        val interests = mappedHobbies.map(MappedHobby::code).distinct()
        return PreparedBioRequest(
            request =
                BioTemplateRequest(
                    jobCategory =
                        ReviewedBioAliases.jobs[profile.jobTitle.aliasKey()]
                            ?: SafeJobCode.OTHER,
                    interests = interests,
                ),
            selectedHobby = selectedHobby,
        )
    }

    fun compose(
        template: GeneratedBioTemplate,
        profile: PersonProfile,
        selectedHobby: String,
    ): GeneratedBio = GeneratedBio.compose(template, profile, selectedHobby)

    private fun requireCompositionFits(
        profile: PersonProfile,
        selectedHobby: String,
    ) {
        if (selectedHobby !in profile.hobbies) {
            throw BioCompositionDoesNotFitException()
        }
        val selectedSourceCodePoints =
            profile.name.codePointCount(0, profile.name.length) +
                profile.jobTitle.codePointCount(0, profile.jobTitle.length) +
                selectedHobby.codePointCount(0, selectedHobby.length)
        if (selectedSourceCodePoints > MAX_SELECTED_SOURCE_CODE_POINTS) {
            throw BioCompositionDoesNotFitException()
        }
    }

    companion object {
        const val FINAL_BIO_MAX_CODE_POINTS = 480
        const val MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS = 260
        const val MAX_SELECTED_SOURCE_CODE_POINTS =
            FINAL_BIO_MAX_CODE_POINTS - MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS
    }
}

data class PreparedBioRequest(
    val request: BioTemplateRequest,
    val selectedHobby: String,
)

class UnsafeBioInputException : RuntimeException()

class BioCompositionDoesNotFitException : RuntimeException()

private data class MappedHobby(
    val original: String,
    val code: SafeInterestCode,
    val hasReviewedAlias: Boolean,
)

internal object ReviewedBioAliases {
    val jobs: Map<String, SafeJobCode> =
        mapOf(
            "software engineer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
            "software developer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
            "web developer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
            "programmer" to SafeJobCode.TECHNOLOGY_ENGINEERING,
            "doctor" to SafeJobCode.HEALTHCARE,
            "nurse" to SafeJobCode.HEALTHCARE,
            "teacher" to SafeJobCode.EDUCATION_RESEARCH,
            "lecturer" to SafeJobCode.EDUCATION_RESEARCH,
            "researcher" to SafeJobCode.EDUCATION_RESEARCH,
            "designer" to SafeJobCode.CREATIVE_MEDIA,
            "artist" to SafeJobCode.CREATIVE_MEDIA,
            "writer" to SafeJobCode.CREATIVE_MEDIA,
            "journalist" to SafeJobCode.CREATIVE_MEDIA,
            "project manager" to SafeJobCode.BUSINESS_OPERATIONS,
            "operations manager" to SafeJobCode.BUSINESS_OPERATIONS,
            "administrator" to SafeJobCode.BUSINESS_OPERATIONS,
            "accountant" to SafeJobCode.FINANCE_LEGAL,
            "lawyer" to SafeJobCode.FINANCE_LEGAL,
            "solicitor" to SafeJobCode.FINANCE_LEGAL,
            "salesperson" to SafeJobCode.SALES_SERVICE,
            "customer service representative" to SafeJobCode.SALES_SERVICE,
            "electrician" to SafeJobCode.TRADES_MANUFACTURING,
            "plumber" to SafeJobCode.TRADES_MANUFACTURING,
            "mechanic" to SafeJobCode.TRADES_MANUFACTURING,
            "carpenter" to SafeJobCode.TRADES_MANUFACTURING,
            "chef" to SafeJobCode.HOSPITALITY_RETAIL,
            "barista" to SafeJobCode.HOSPITALITY_RETAIL,
            "retail assistant" to SafeJobCode.HOSPITALITY_RETAIL,
            "firefighter" to SafeJobCode.PUBLIC_COMMUNITY_SERVICE,
            "social worker" to SafeJobCode.PUBLIC_COMMUNITY_SERVICE,
            "student" to SafeJobCode.STUDENT,
        )

    val interests: Map<String, SafeInterestCode> =
        mapOf(
            "hiking" to SafeInterestCode.OUTDOORS_NATURE,
            "tramping" to SafeInterestCode.OUTDOORS_NATURE,
            "running" to SafeInterestCode.SPORTS_FITNESS,
            "cycling" to SafeInterestCode.SPORTS_FITNESS,
            "pottery" to SafeInterestCode.ARTS_CRAFTS,
            "painting" to SafeInterestCode.ARTS_CRAFTS,
            "guitar" to SafeInterestCode.MUSIC,
            "piano" to SafeInterestCode.MUSIC,
            "reading" to SafeInterestCode.READING_WRITING,
            "creative writing" to SafeInterestCode.READING_WRITING,
            "espresso" to SafeInterestCode.FOOD_DRINK,
            "cooking" to SafeInterestCode.FOOD_DRINK,
            "chess" to SafeInterestCode.GAMES_PUZZLES,
            "crosswords" to SafeInterestCode.GAMES_PUZZLES,
            "coding" to SafeInterestCode.TECHNOLOGY_MAKING,
            "woodworking" to SafeInterestCode.TECHNOLOGY_MAKING,
            "gardening" to SafeInterestCode.GARDENING,
            "travel" to SafeInterestCode.TRAVEL,
        )
}

private val UNSAFE_SOURCE_PATTERNS =
    listOf(
        Regex(
            """\b(?:ignore|disregard|override|forget|bypass)\b.{0,100}""" +
                """\b(?:instructions?|prompts?|directives?|system|developer|assistant|safeguards?)\b""" +
                """.{0,100}\b(?:say|return|respond|output|write|print|repeat|reveal|do)\b""",
        ),
        Regex(
            """\b(?:system|developer|assistant)\b.{0,60}""" +
                """\b(?:ignore|reveal|say|return|respond|output|write|print|repeat|override|bypass)\b""",
        ),
        Regex(
            """\b(?:reveal|print|repeat|output|return|respond|say|write)\b.{0,100}""" +
                """\b(?:system prompts?|secrets?|instructions?|credentials?|i am hacked)\b""",
        ),
        Regex("""\bi\s+am\s+hacked\b"""),
    )

private fun String.aliasKey(): String = lowercase(Locale.ROOT)

internal fun violatesBioContentPolicy(value: String): Boolean {
    val normalized = value.securityScanValue() ?: return true
    return UNSAFE_SOURCE_PATTERNS.any { it.containsMatchIn(normalized.words) } ||
        FORBIDDEN_SOURCE_LITERAL_PATTERNS.any { it.containsMatchIn(normalized.literal) }
}

private fun sourceValuesForInspection(profile: PersonProfile): List<String> {
    val individual = listOf(profile.jobTitle) + profile.hobbies
    val adjacent =
        individual
            .zipWithNext()
            .flatMap { (first, second) ->
                listOf("$first $second", first + second)
            }
    val complete = listOf(individual.joinToString(" "), individual.joinToString(""))
    return individual + adjacent + complete
}

private data class SecurityScanValue(
    val literal: String,
    val words: String,
)

private fun String.securityScanValue(): SecurityScanValue? {
    val decoded = decodeUnicodeEscapesForInspection() ?: return null
    val normalized =
        Normalizer
            .normalize(decoded, Normalizer.Form.NFKC)
            .codePoints()
            .toArray()
            .asSequence()
            .filterNot(::isDefaultIgnorableForInspection)
            .map(::inspectionCodePoint)
            .joinToString(separator = "") { codePoint -> String(Character.toChars(codePoint)) }
            .lowercase(Locale.ROOT)
    return SecurityScanValue(
        literal = normalized,
        words =
            normalized
                .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                .trim()
                .replace(Regex("""\s+"""), " "),
    )
}

private fun String.decodeUnicodeEscapesForInspection(): String? {
    var invalidEscape = false
    val decoded =
        UNICODE_ESCAPE.replace(this) { match ->
            val codePoint =
                match.groupValues[1]
                    .ifEmpty { match.groupValues[2] }
                    .toInt(16)
            if (Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                invalidEscape = true
                ""
            }
        }
    return decoded.takeUnless { invalidEscape }
}

private fun inspectionCodePoint(codePoint: Int): Int =
    CONFUSABLE_ASCII[codePoint] ?: codePoint

private fun isDefaultIgnorableForInspection(codePoint: Int): Boolean {
    if (Character.getType(codePoint) == Character.FORMAT.toInt()) {
        return true
    }
    return codePoint in MONGOLIAN_VARIATION_SELECTORS ||
        codePoint == MONGOLIAN_FREE_VARIATION_SELECTOR_FOUR ||
        codePoint in STANDARD_VARIATION_SELECTORS ||
        codePoint in SUPPLEMENTARY_VARIATION_SELECTORS
}

private val FORBIDDEN_SOURCE_LITERAL_PATTERNS =
    listOf(
        Regex("""(?i)<\|(?:system|developer|assistant|user)\|>"""),
        Regex("""(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"""),
        Regex("""(?i)\bbearer\s+[a-z0-9._~+/=-]{12,}\b"""),
        Regex("""(?i)\b(?:api[_ -]?key|secret|password)\s*[:=]\s*\S{8,}"""),
        Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"""),
        Regex("""(?i)\bhttps?://\S+"""),
        Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""),
        Regex("""(?<!\d)(?:\+?\d[\d .()-]{7,}\d)(?!\d)"""),
    )

private val UNICODE_ESCAPE = Regex("""\\u(?:\{([0-9A-Fa-f]{1,6})}|([0-9A-Fa-f]{4}))""")

private val MONGOLIAN_VARIATION_SELECTORS = 0x180B..0x180D
private const val MONGOLIAN_FREE_VARIATION_SELECTOR_FOUR = 0x180F
private val STANDARD_VARIATION_SELECTORS = 0xFE00..0xFE0F
private val SUPPLEMENTARY_VARIATION_SELECTORS = 0xE0100..0xE01EF

private val CONFUSABLE_ASCII =
    mapOf(
        0x0391 to 'a'.code,
        0x0392 to 'b'.code,
        0x0395 to 'e'.code,
        0x0397 to 'h'.code,
        0x0399 to 'i'.code,
        0x039A to 'k'.code,
        0x039C to 'm'.code,
        0x039D to 'n'.code,
        0x039F to 'o'.code,
        0x03A1 to 'p'.code,
        0x03A4 to 't'.code,
        0x03A7 to 'x'.code,
        0x03B1 to 'a'.code,
        0x03B5 to 'e'.code,
        0x03B9 to 'i'.code,
        0x03BA to 'k'.code,
        0x03BF to 'o'.code,
        0x03C1 to 'p'.code,
        0x03C4 to 't'.code,
        0x03C7 to 'x'.code,
        0x0406 to 'i'.code,
        0x0410 to 'a'.code,
        0x0412 to 'b'.code,
        0x0415 to 'e'.code,
        0x041A to 'k'.code,
        0x041C to 'm'.code,
        0x041D to 'h'.code,
        0x041E to 'o'.code,
        0x0420 to 'p'.code,
        0x0421 to 'c'.code,
        0x0422 to 't'.code,
        0x0425 to 'x'.code,
        0x0430 to 'a'.code,
        0x0435 to 'e'.code,
        0x043A to 'k'.code,
        0x043C to 'm'.code,
        0x043D to 'h'.code,
        0x043E to 'o'.code,
        0x0440 to 'p'.code,
        0x0441 to 'c'.code,
        0x0442 to 't'.code,
        0x0445 to 'x'.code,
        0x0456 to 'i'.code,
        0x0458 to 'j'.code,
    )
