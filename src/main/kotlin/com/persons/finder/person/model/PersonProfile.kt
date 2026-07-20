package com.persons.finder.person.model

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

        fun create(
            name: String,
            jobTitle: String,
            hobbies: List<String>,
        ): PersonProfile {
            if (hobbies.isEmpty()) {
                throw ProfileValidationException(
                    ProfileValidationField.HOBBIES,
                    ProfileValidationReason.REQUIRED,
                )
            }
            if (hobbies.size > MAX_HOBBIES) {
                throw ProfileValidationException(
                    ProfileValidationField.HOBBIES,
                    ProfileValidationReason.TOO_MANY_ITEMS,
                )
            }

            val canonicalName =
                canonicalizeProfileText(
                    value = name,
                    maximumCodePoints = MAX_NAME_CODE_POINTS,
                    tooLongField = ProfileValidationField.NAME,
                )
            val canonicalJobTitle =
                canonicalizeProfileText(
                    value = jobTitle,
                    maximumCodePoints = MAX_JOB_TITLE_CODE_POINTS,
                    tooLongField = ProfileValidationField.JOB_TITLE,
                )
            val canonicalHobbies =
                hobbies
                    .map {
                        canonicalizeProfileText(
                            value = it,
                            maximumCodePoints = MAX_HOBBY_CODE_POINTS,
                            tooLongField = ProfileValidationField.HOBBIES,
                        )
                    }
                    .distinct()

            return PersonProfile(
                name = canonicalName,
                jobTitle = canonicalJobTitle,
                hobbies = canonicalHobbies,
            )
        }
    }
}

enum class ProfileValidationField {
    PROFILE,
    NAME,
    JOB_TITLE,
    HOBBIES,
}

enum class ProfileValidationReason {
    REQUIRED,
    INVALID_FORMAT,
    TOO_LONG,
    TOO_MANY_ITEMS,
}

class ProfileValidationException(
    val field: ProfileValidationField,
    val reason: ProfileValidationReason,
) : IllegalArgumentException()

private fun canonicalizeProfileText(
    value: String,
    maximumCodePoints: Int,
    tooLongField: ProfileValidationField,
): String {
    if (!value.isWellFormedUtf16() || value.codePoints().anyMatch(::isForbiddenProfileCodePoint)) {
        throw ProfileValidationException(
            ProfileValidationField.PROFILE,
            ProfileValidationReason.INVALID_FORMAT,
        )
    }

    val canonical = Normalizer.normalize(value.trimUnicodeWhitespace(), Normalizer.Form.NFC)
    if (canonical.isEmpty()) {
        throw ProfileValidationException(
            ProfileValidationField.PROFILE,
            ProfileValidationReason.REQUIRED,
        )
    }
    if (canonical.codePointCount() > maximumCodePoints) {
        throw ProfileValidationException(
            tooLongField,
            ProfileValidationReason.TOO_LONG,
        )
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
