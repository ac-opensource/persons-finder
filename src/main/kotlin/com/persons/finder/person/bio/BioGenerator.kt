package com.persons.finder.person.bio

import java.time.Duration
import java.util.concurrent.CancellationException

internal val BIO_GENERATION_DEADLINE: Duration = Duration.ofSeconds(10)

/**
 * Provider/model-neutral boundary. This typed request is the hard egress
 * allowlist: it cannot carry raw profile text, coordinates, or identifiers.
 */
fun interface BioGenerator {
    fun generate(request: BioTemplateRequest): BioGenerationResult

    fun generate(
        request: BioTemplateRequest,
        context: BioGenerationContext,
    ): BioGenerationResult {
        try {
            context.requireRemaining()
        } catch (_: BioGenerationDeadlineExceededException) {
            return BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT)
        }
        val result = generate(request)
        return try {
            context.requireRemaining()
            result
        } catch (_: BioGenerationDeadlineExceededException) {
            BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT)
        }
    }
}

class BioTemplateRequest(
    val displayName: String = DISPLAY_NAME_TOKEN,
    val locale: String = DEPLOYMENT_LOCALE,
    val countryCode: String = DEPLOYMENT_COUNTRY_CODE,
    val jobCategory: SafeJobCode,
    val jobCategoryMappingVersion: String = JOB_MAPPING_VERSION,
    interests: List<SafeInterestCode>,
    val interestCategoryMappingVersion: String = INTEREST_MAPPING_VERSION,
    val macroRegion: MacroRegion? = null,
    val tone: BioTone = BioTone.QUIRKY,
) {
    val interests: List<SafeInterestCode> = interests.toList()

    init {
        require(displayName == DISPLAY_NAME_TOKEN)
        require(locale == DEPLOYMENT_LOCALE)
        require(countryCode == DEPLOYMENT_COUNTRY_CODE)
        require(jobCategoryMappingVersion == JOB_MAPPING_VERSION)
        require(interests.isNotEmpty())
        require(interests.distinct() == interests)
        require(interestCategoryMappingVersion == INTEREST_MAPPING_VERSION)
        require(tone == BioTone.QUIRKY)
    }

    override fun equals(other: Any?): Boolean =
        other is BioTemplateRequest &&
            displayName == other.displayName &&
            locale == other.locale &&
            countryCode == other.countryCode &&
            jobCategory == other.jobCategory &&
            jobCategoryMappingVersion == other.jobCategoryMappingVersion &&
            interests == other.interests &&
            interestCategoryMappingVersion == other.interestCategoryMappingVersion &&
            macroRegion == other.macroRegion &&
            tone == other.tone

    override fun hashCode(): Int =
        listOf(
            displayName,
            locale,
            countryCode,
            jobCategory,
            jobCategoryMappingVersion,
            interests,
            interestCategoryMappingVersion,
            macroRegion,
            tone,
        ).hashCode()

    override fun toString(): String =
        "BioTemplateRequest(" +
            "interestCount=${interests.size}, macroRegionPresent=${macroRegion != null})"

    companion object {
        const val DISPLAY_NAME_TOKEN = "{{NAME}}"
        const val DEPLOYMENT_LOCALE = "en-NZ"
        const val DEPLOYMENT_COUNTRY_CODE = "NZ"
        const val JOB_MAPPING_VERSION = "job-v1"
        const val INTEREST_MAPPING_VERSION = "interest-v1"
    }
}

enum class SafeJobCode(val wireValue: String) {
    TECHNOLOGY_ENGINEERING("technology_engineering"),
    HEALTHCARE("healthcare"),
    EDUCATION_RESEARCH("education_research"),
    CREATIVE_MEDIA("creative_media"),
    BUSINESS_OPERATIONS("business_operations"),
    FINANCE_LEGAL("finance_legal"),
    SALES_SERVICE("sales_service"),
    TRADES_MANUFACTURING("trades_manufacturing"),
    HOSPITALITY_RETAIL("hospitality_retail"),
    PUBLIC_COMMUNITY_SERVICE("public_community_service"),
    STUDENT("student"),
    OTHER("other"),
}

enum class SafeInterestCode(val wireValue: String) {
    OUTDOORS_NATURE("outdoors_nature"),
    SPORTS_FITNESS("sports_fitness"),
    ARTS_CRAFTS("arts_crafts"),
    MUSIC("music"),
    READING_WRITING("reading_writing"),
    FOOD_DRINK("food_drink"),
    GAMES_PUZZLES("games_puzzles"),
    TECHNOLOGY_MAKING("technology_making"),
    GARDENING("gardening"),
    TRAVEL("travel"),
    OTHER("other"),
}

enum class MacroRegion(val wireValue: String) {
    NORTH_ISLAND("North Island"),
    SOUTH_ISLAND("South Island"),
}

enum class BioTone {
    QUIRKY,
}

enum class BioTemplateId(val wireValue: String) {
    QUIRKY_SIDE_QUEST("quirky_side_quest"),
    DELIGHTFUL_TWIST("delightful_twist"),
    CURIOUS_ADVENTURE("curious_adventure"),
    ;

    companion object {
        fun fromWireValue(value: String): BioTemplateId? =
            entries.firstOrNull { it.wireValue == value }
    }
}

sealed interface BioGenerationResult {
    data class Template(val value: GeneratedBioTemplate) : BioGenerationResult {
        override fun toString(): String = "Template(validated=true)"
    }

    data class Failure(val reason: BioGenerationFailure) : BioGenerationResult
}

enum class BioGenerationFailure {
    TIMEOUT,
    RATE_LIMITED,
    UNAVAILABLE,
    INVALID_OUTPUT,
    POLICY_REJECTED,
}

/**
 * One application-owned monotonic deadline covers generation. Network adapters
 * clamp their own timeout to [remaining] and must propagate cancellation.
 */
class BioGenerationContext private constructor(
    private val deadlineNanos: Long,
    private val ticker: () -> Long,
) {
    fun remaining(): Duration {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Bio generation was cancelled")
        }
        val remainingNanos = deadlineNanos - ticker()
        return if (remainingNanos <= 0) Duration.ZERO else Duration.ofNanos(remainingNanos)
    }

    fun requireRemaining(): Duration =
        remaining().takeUnless(Duration::isZero)
            ?: throw BioGenerationDeadlineExceededException()

    companion object {
        fun start(
            timeout: Duration,
            ticker: () -> Long = System::nanoTime,
        ): BioGenerationContext {
            require(!timeout.isZero && !timeout.isNegative)
            val startedAt = ticker()
            return BioGenerationContext(
                deadlineNanos = Math.addExact(startedAt, timeout.toNanos()),
                ticker = ticker,
            )
        }
    }
}

class BioGenerationDeadlineExceededException : RuntimeException()
