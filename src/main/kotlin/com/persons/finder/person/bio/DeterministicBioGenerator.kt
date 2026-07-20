package com.persons.finder.person.bio

class DeterministicBioGenerator : BioGenerator {
    override fun generate(request: BioTemplateRequest): BioGenerationResult =
        generate(request, BioGenerationContext.start(BIO_GENERATION_DEADLINE))

    override fun generate(
        request: BioTemplateRequest,
        context: BioGenerationContext,
    ): BioGenerationResult {
        try {
            context.requireRemaining()
        } catch (_: BioGenerationDeadlineExceededException) {
            return BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT)
        }
        val primaryInterest =
            request.interests.firstOrNull { it != SafeInterestCode.OTHER }
                ?: SafeInterestCode.OTHER
        val catalogIndex =
            (request.jobCategory.ordinal * SafeInterestCode.entries.size + primaryInterest.ordinal) %
                BioTemplateId.entries.size
        return BioGenerationResult.Template(
            GeneratedBioTemplate.fromCatalog(BioTemplateId.entries[catalogIndex]),
        )
    }
}
