package com.persons.finder.person.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PersonModelTest {
    @Test
    fun `profile canonicalizes and deduplicates hobbies in first-input order`() {
        val profile =
            PersonProfile.create(
                name = "  Andrew  ",
                jobTitle = "Software engineer",
                hobbies = listOf("hiking", "hiking", "espresso"),
            )

        assertEquals("Andrew", profile.name)
        assertEquals(listOf("hiking", "espresso"), profile.hobbies)
    }

    @Test
    fun `profile validation exposes typed field and reason without HTTP concerns`() {
        val tooLong =
            assertThrows(ProfileValidationException::class.java) {
                PersonProfile.create(
                    name = "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS + 1),
                    jobTitle = "Engineer",
                    hobbies = listOf("hiking"),
                )
            }
        assertEquals(ProfileValidationField.NAME, tooLong.field)
        assertEquals(ProfileValidationReason.TOO_LONG, tooLong.reason)

        val missing =
            assertThrows(ProfileValidationException::class.java) {
                PersonProfile.create(
                    name = "Andrew",
                    jobTitle = "Engineer",
                    hobbies = emptyList(),
                )
            }
        assertEquals(ProfileValidationField.HOBBIES, missing.field)
        assertEquals(ProfileValidationReason.REQUIRED, missing.reason)
    }

    @Test
    fun `profile rejects deceptive format controls while preserving joiners`() {
        listOf(
            "\u200B", // zero-width space
            "\u202E", // right-to-left override
            "\u2066", // left-to-right isolate
            "\uFEFF", // zero-width no-break space / BOM
        ).forEach { formatControl ->
            val failure =
                assertThrows(ProfileValidationException::class.java) {
                    PersonProfile.create(
                        name = "Andrew",
                        jobTitle = "Engineer",
                        hobbies = listOf("hiking${formatControl}club"),
                    )
                }

            assertEquals(ProfileValidationField.PROFILE, failure.field)
            assertEquals(ProfileValidationReason.INVALID_FORMAT, failure.reason)
        }

        val profile =
            PersonProfile.create(
                name = "Andrew",
                jobTitle = "Engineer",
                hobbies = listOf("emoji 👨‍💻", "می‌خواهم"),
            )
        assertEquals(listOf("emoji 👨‍💻", "می‌خواهم"), profile.hobbies)
    }

    @Test
    fun `capturedAt accepts only restricted millisecond RFC 3339`() {
        assertEquals(
            "2026-07-19T05:06:07.123Z",
            CapturedAt.parse("2026-07-19T17:06:07.123+12:00").value.toString(),
        )
        listOf(
            "2026-07-19 05:06:07Z",
            "2026-07-19T05:06:07.1234Z",
            "2026-07-19T05:06:60Z",
            "2026-07-19T05:06:07z",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                CapturedAt.parse(it)
            }
        }
    }

    @Test
    fun `coordinate canonicalization has one exact representation`() {
        assertEquals(GeoPoint.from(-0.0, 180.0), GeoPoint.from(0.0, -180.0))
        assertEquals(GeoPoint.from(90.0, 42.0), GeoPoint.from(90.0, 0.0))
    }
}
