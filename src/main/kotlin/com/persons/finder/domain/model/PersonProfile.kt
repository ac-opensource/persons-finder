package com.persons.finder.domain.model

import java.text.Normalizer

class PersonProfile private constructor(
    val name: String,
    val jobTitle: String,
    hobbies: List<String>,
) {
    val hobbies: List<String> = hobbies.toList()

    override fun equals(other: Any?): Boolean =
        other is PersonProfile &&
            name == other.name &&
            jobTitle == other.jobTitle &&
            hobbies == other.hobbies

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + jobTitle.hashCode()
        result = 31 * result + hobbies.hashCode()
        return result
    }

    companion object {
        const val MAX_NAME_CODE_POINTS = 80
        const val MAX_JOB_TITLE_CODE_POINTS = 80
        const val MAX_HOBBY_CODE_POINTS = 60
        const val MAX_HOBBIES = 10
        const val FINAL_BIO_MAX_CODE_POINTS = 240
        const val MINIMUM_BIO_TEMPLATE_OVERHEAD_CODE_POINTS = 34
        const val MAX_SELECTED_SOURCE_CODE_POINTS =
            FINAL_BIO_MAX_CODE_POINTS - MINIMUM_BIO_TEMPLATE_OVERHEAD_CODE_POINTS

        fun create(
            name: String,
            jobTitle: String,
            hobbies: List<String>,
        ): PersonProfile {
            require(hobbies.isNotEmpty()) { "At least one hobby is required" }
            require(hobbies.size <= MAX_HOBBIES) { "Too many hobbies" }

            val canonicalName = canonicalizeProfileText(name, MAX_NAME_CODE_POINTS)
            val canonicalJobTitle = canonicalizeProfileText(jobTitle, MAX_JOB_TITLE_CODE_POINTS)
            val canonicalHobbies =
                hobbies
                    .map { canonicalizeProfileText(it, MAX_HOBBY_CODE_POINTS) }
                    .distinct()

            return PersonProfile(
                name = canonicalName,
                jobTitle = canonicalJobTitle,
                hobbies = canonicalHobbies,
            )
        }

        fun requireBioCompositionFits(
            profile: PersonProfile,
            selectedHobby: String,
        ) {
            require(selectedHobby in profile.hobbies) {
                "The selected hobby must come from the canonical profile"
            }
            val selectedSourceCodePoints =
                profile.name.codePointCount() +
                    profile.jobTitle.codePointCount() +
                    selectedHobby.codePointCount()
            require(selectedSourceCodePoints <= MAX_SELECTED_SOURCE_CODE_POINTS) {
                "Selected profile text cannot fit the final bio contract"
            }
        }
    }
}

private fun canonicalizeProfileText(
    value: String,
    maximumCodePoints: Int,
): String {
    require(value.isWellFormedUtf16()) { "Malformed Unicode is not allowed" }
    require(value.codePoints().noneMatch(::isForbiddenProfileCodePoint)) {
        "Control and line separator characters are not allowed"
    }

    val canonical = Normalizer.normalize(value.trimUnicodeWhitespace(), Normalizer.Form.NFC)
    require(canonical.isNotEmpty()) { "Profile text must not be blank" }
    require(canonical.codePointCount() <= maximumCodePoints) {
        "Profile text exceeds its field limit"
    }
    return canonical
}

private fun String.codePointCount(): Int = codePointCount(0, length)

private fun String.isWellFormedUtf16(): Boolean {
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            Character.isHighSurrogate(character) -> {
                if (index + 1 >= length || !Character.isLowSurrogate(this[index + 1])) {
                    return false
                }
                index += 2
            }

            Character.isLowSurrogate(character) -> return false
            else -> index++
        }
    }
    return true
}

private fun String.trimUnicodeWhitespace(): String {
    var start = 0
    var end = length

    while (start < end) {
        val codePoint = codePointAt(start)
        if (!codePoint.isUnicodeWhitespace()) {
            break
        }
        start += Character.charCount(codePoint)
    }

    while (end > start) {
        val codePoint = codePointBefore(end)
        if (!codePoint.isUnicodeWhitespace()) {
            break
        }
        end -= Character.charCount(codePoint)
    }

    return substring(start, end)
}

private fun Int.isUnicodeWhitespace(): Boolean =
    Character.isWhitespace(this) || Character.isSpaceChar(this)

private fun isForbiddenProfileCodePoint(codePoint: Int): Boolean =
    when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        -> true

        else -> false
    }
