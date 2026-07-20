package com.persons.finder.person.create


class DeterministicBioGenerator : BioGenerator {
    override fun generate(request: BioTemplateRequest): BioGenerationResult {
        val primaryInterest =
            request.interests.firstOrNull { it != SafeInterestCode.OTHER }
                ?: SafeInterestCode.OTHER
        return BioGenerationResult.Template(
            "{{NAME}}, a quirky {{JOB}}, " +
                "${request.jobCategory.modifier()} ${primaryInterest.verb()} {{HOBBY}}.",
        )
    }
}

private fun SafeJobCode.modifier(): String =
    when (this) {
        SafeJobCode.TECHNOLOGY_ENGINEERING -> "deftly"
        SafeJobCode.HEALTHCARE -> "warmly"
        SafeJobCode.EDUCATION_RESEARCH -> "keenly"
        SafeJobCode.CREATIVE_MEDIA -> "boldly"
        SafeJobCode.BUSINESS_OPERATIONS -> "briskly"
        SafeJobCode.FINANCE_LEGAL -> "neatly"
        SafeJobCode.SALES_SERVICE -> "brightly"
        SafeJobCode.TRADES_MANUFACTURING -> "handily"
        SafeJobCode.HOSPITALITY_RETAIL -> "cheerily"
        SafeJobCode.PUBLIC_COMMUNITY_SERVICE -> "kindly"
        SafeJobCode.STUDENT -> "eagerly"
        SafeJobCode.OTHER -> "oddly"
    }

private fun SafeInterestCode.verb(): String =
    when (this) {
        SafeInterestCode.OUTDOORS_NATURE -> "enjoys"
        SafeInterestCode.SPORTS_FITNESS -> "pursues"
        SafeInterestCode.ARTS_CRAFTS -> "adores"
        SafeInterestCode.MUSIC -> "treasures"
        SafeInterestCode.READING_WRITING -> "relishes"
        SafeInterestCode.FOOD_DRINK -> "savors"
        SafeInterestCode.GAMES_PUZZLES -> "prizes"
        SafeInterestCode.TECHNOLOGY_MAKING -> "explores"
        SafeInterestCode.GARDENING -> "celebrates"
        SafeInterestCode.TRAVEL -> "seeks"
        SafeInterestCode.OTHER -> "likes"
    }
