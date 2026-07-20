package com.persons.finder.person.create

import com.persons.finder.person.model.PersonProfile
import java.util.Locale

class BioPolicy {
    fun prepare(profile: PersonProfile): PreparedBioRequest {
        val mappedHobbies =
            profile.hobbies.map { hobby ->
                MappedHobby(
                    original = hobby,
                    code = INTEREST_ALIASES[hobby.aliasKey()] ?: SafeInterestCode.OTHER,
                    hasReviewedAlias = INTEREST_ALIASES.containsKey(hobby.aliasKey()),
                )
            }
        val selectedHobby =
            mappedHobbies.firstOrNull(MappedHobby::hasReviewedAlias)?.original
                ?: mappedHobbies.first().original
        requireCompositionFits(profile, selectedHobby)

        if (isUnsafeBioSource(profile.jobTitle) || profile.hobbies.any(::isUnsafeBioSource)) {
            throw UnsafeBioInputException()
        }

        val interests = mappedHobbies.map(MappedHobby::code).distinct()
        return PreparedBioRequest(
            request =
                BioTemplateRequest(
                    jobCategory = JOB_ALIASES[profile.jobTitle.aliasKey()] ?: SafeJobCode.OTHER,
                    interests = interests,
                ),
            selectedHobby = selectedHobby,
        )
    }

    fun compose(
        template: String,
        profile: PersonProfile,
        selectedHobby: String,
    ): String {
        validateTemplate(template)

        val values =
            mapOf(
                "NAME" to profile.name,
                "JOB" to profile.jobTitle,
                "HOBBY" to selectedHobby,
            )
        val rendered = StringBuilder()
        var cursor = 0
        APPROVED_TOKEN.findAll(template).forEach { match ->
            rendered.append(template, cursor, match.range.first)
            rendered.append(values.getValue(match.groupValues[1]))
            cursor = match.range.last + 1
        }
        rendered.append(template, cursor, template.length)

        val bio = rendered.toString()
        require(isOneSafeSentence(bio)) { "Composed bio must be one safe sentence" }
        require(bio.codePointCount(0, bio.length) <= FINAL_BIO_MAX_CODE_POINTS) {
            "Composed bio exceeds its final limit"
        }
        return bio
    }

    private fun validateTemplate(template: String) {
        require(template.isNotBlank()) { "Generated template must not be blank" }
        require(template.isWellFormedUtf16()) { "Generated template contains malformed Unicode" }
        require(template.codePoints().noneMatch(::isForbiddenOutputCodePoint)) {
            "Generated template contains forbidden controls"
        }
        require(!isUnsafeBioSource(template)) {
            "Generated template failed the safe-output policy"
        }
        REQUIRED_TOKENS.forEach { token ->
            require(token.toRegex(RegexOption.LITERAL).findAll(template).count() == 1) {
                "Generated template has invalid placeholder cardinality"
            }
        }

        val withoutApprovedTokens = REQUIRED_TOKENS.fold(template) { value, token ->
            value.replace(token, "")
        }
        require(!withoutApprovedTokens.contains("{{") && !withoutApprovedTokens.contains("}}")) {
            "Generated template contains an unknown placeholder"
        }
        require(DISALLOWED_REGION_TERMS.none { template.contains(it, ignoreCase = true) }) {
            "Generated template discloses a disallowed region"
        }
    }

    private fun isOneSafeSentence(value: String): Boolean {
        if (value.isBlank() || value.codePoints().anyMatch(::isForbiddenOutputCodePoint)) {
            return false
        }
        return !hasUnrecognizedInternalSentenceBoundary(value)
    }

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
        const val FINAL_BIO_MAX_CODE_POINTS = 240
        const val MINIMUM_BIO_TEMPLATE_OVERHEAD_CODE_POINTS = 34
        const val MAX_SELECTED_SOURCE_CODE_POINTS =
            FINAL_BIO_MAX_CODE_POINTS - MINIMUM_BIO_TEMPLATE_OVERHEAD_CODE_POINTS
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

private val JOB_ALIASES =
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

private val INTEREST_ALIASES =
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

private val UNSAFE_SOURCE_PATTERNS =
    listOf(
        Regex("""(?i)\b(?:ignore|disregard|override)\b.{0,40}\b(?:instruction|prompt|system|developer)\b"""),
        Regex("""(?i)\b(?:system|developer|assistant)\s*(?:message|prompt|instruction)?\s*:"""),
        Regex("""(?i)\b(?:reveal|print|repeat)\b.{0,40}\b(?:system prompt|secret|instruction)\b"""),
        Regex("""(?i)<\|(?:system|developer|assistant|user)\|>"""),
        Regex("""(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"""),
        Regex("""(?i)\bbearer\s+[a-z0-9._~+/=-]{12,}\b"""),
        Regex("""(?i)\b(?:api[_ -]?key|secret|password)\s*[:=]\s*\S{8,}"""),
        Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"""),
        Regex("""(?i)\bhttps?://\S+"""),
        Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""),
        Regex("""(?<!\d)(?:\+?\d[\d .()-]{7,}\d)(?!\d)"""),
    )

private val REQUIRED_TOKENS = listOf("{{NAME}}", "{{JOB}}", "{{HOBBY}}")
private val APPROVED_TOKEN = Regex("""\{\{(NAME|JOB|HOBBY)}}""")
private val DISALLOWED_REGION_TERMS = listOf("North Island", "South Island")
private val SENTENCE_BOUNDARY_WITH_CONTINUATION =
    Regex("""([.!?])(?:["')\]]*)\s*(?=\S)""")
private val PRECEDING_WORD = Regex("""([\p{L}]+(?:\.[\p{L}]+)*)$""")
private val NON_TERMINAL_PERIOD_WORDS =
    setOf("dr", "e.g", "i.e", "jr", "mr", "mrs", "ms", "prof", "sr", "st", "vs")

private fun String.aliasKey(): String = lowercase(Locale.ROOT)

private fun isUnsafeBioSource(value: String): Boolean =
    UNSAFE_SOURCE_PATTERNS.any { it.containsMatchIn(value) }

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

private fun isForbiddenOutputCodePoint(codePoint: Int): Boolean =
    when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        -> true

        else -> false
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
