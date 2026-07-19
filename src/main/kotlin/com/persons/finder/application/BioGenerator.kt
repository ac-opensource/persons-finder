package com.persons.finder.application

/**
 * Provider/model-neutral boundary. This typed request is the hard egress
 * allowlist: it cannot carry raw profile text, coordinates, or identifiers.
 */
fun interface BioGenerator {
    fun generate(request: BioTemplateRequest): BioGenerationResult
}

data class BioTemplateRequest(
    val displayName: String = DISPLAY_NAME_TOKEN,
    val locale: String = DEPLOYMENT_LOCALE,
    val countryCode: String = DEPLOYMENT_COUNTRY_CODE,
    val jobCategory: SafeJobCode,
    val jobCategoryMappingVersion: String = JOB_MAPPING_VERSION,
    val interests: List<SafeInterestCode>,
    val interestCategoryMappingVersion: String = INTEREST_MAPPING_VERSION,
    val macroRegion: MacroRegion? = null,
    val tone: BioTone = BioTone.QUIRKY,
) {
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

enum class MacroRegion {
    NORTH_ISLAND,
    SOUTH_ISLAND,
}

enum class BioTone {
    QUIRKY,
}

sealed interface BioGenerationResult {
    data class Template(val value: String) : BioGenerationResult

    data class Failure(val reason: BioGenerationFailure) : BioGenerationResult
}

enum class BioGenerationFailure {
    UNAVAILABLE,
    MALFORMED_OUTPUT,
    UNSAFE_OUTPUT,
}
