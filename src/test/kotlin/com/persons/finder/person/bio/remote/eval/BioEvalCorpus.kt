package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import java.security.MessageDigest
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal data class BioEvalCase(
    val id: String,
    val slices: Set<String>,
    val jobCategory: SafeJobCode,
    val interests: List<SafeInterestCode>,
    val hobbyCount: Int,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Evaluation case id is invalid" }
        require(slices.isNotEmpty()) { "Evaluation case must belong to at least one slice" }
        require(slices.all(SLICE_PATTERN::matches)) { "Evaluation case slice is invalid" }
        require(interests.isNotEmpty()) { "Evaluation case must have at least one interest" }
        require(interests.distinct() == interests) {
            "Evaluation case interests must be unique and ordered"
        }
        require(hobbyCount in 1..BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS) {
            "Evaluation case hobby count is outside the supported range"
        }
        require(slices == expectedSlices()) {
            "Evaluation case slices must match its typed structure"
        }
    }

    fun toRequest(): BioTemplateRequest =
        BioTemplateRequest(
            jobCategory = jobCategory,
            interests = interests,
            hobbyCount = hobbyCount,
        )

    companion object {
        private val ID_PATTERN = Regex("case-[0-9]{3}")
        private val SLICE_PATTERN =
            Regex(
                "(?:job-coverage|single-interest|multi-interest|" +
                    "single-hobby|multi-hobby|maximum-hobbies|both-other)",
            )
    }

    private fun expectedSlices(): Set<String> =
        buildSet {
            add("job-coverage")
            add(if (interests.size == 1) "single-interest" else "multi-interest")
            add(if (hobbyCount == 1) "single-hobby" else "multi-hobby")
            if (hobbyCount == BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS) {
                add("maximum-hobbies")
            }
            if (
                jobCategory == SafeJobCode.OTHER &&
                interests == listOf(SafeInterestCode.OTHER)
            ) {
                add("both-other")
            }
        }
}

internal data class BioEvalCorpus(
    val id: String,
    val schemaVersion: Int,
    val sha256: String,
    val cases: List<BioEvalCase>,
) {
    init {
        require(id == CORPUS_ID) { "Evaluation corpus id is invalid" }
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported evaluation corpus schema version"
        }
        require(SHA_256_PATTERN.matches(sha256)) { "Evaluation corpus hash is invalid" }
        require(cases.isNotEmpty()) { "Evaluation corpus must contain cases" }
        require(cases.map(BioEvalCase::id).distinct().size == cases.size) {
            "Evaluation case ids must be unique"
        }

        val coveredJobs = cases.mapTo(mutableSetOf(), BioEvalCase::jobCategory)
        require(coveredJobs == SafeJobCode.entries.toSet()) {
            "Evaluation corpus must cover every safe job code"
        }

        val coveredInterests =
            cases.flatMapTo(mutableSetOf()) { testCase -> testCase.interests }
        require(coveredInterests == SafeInterestCode.entries.toSet()) {
            "Evaluation corpus must cover every safe interest code"
        }
        require(
            cases.any { testCase ->
                testCase.jobCategory == SafeJobCode.OTHER &&
                    testCase.interests == listOf(SafeInterestCode.OTHER)
            },
        ) {
            "Evaluation corpus must contain a both-other case"
        }
        require(cases.any { testCase -> testCase.interests.size > 1 }) {
            "Evaluation corpus must contain a multi-interest case"
        }
        require(cases.any { testCase -> testCase.hobbyCount > 1 }) {
            "Evaluation corpus must contain a multi-hobby case"
        }
        require(
            cases.any { testCase ->
                testCase.hobbyCount == BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS
            },
        ) {
            "Evaluation corpus must contain a maximum-hobby-count case"
        }
    }

    companion object {
        internal const val SUPPORTED_SCHEMA_VERSION = 2
        private const val CORPUS_ID = "bio-cases-v2"
        private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
    }
}

internal object BioEvalCorpusLoader {
    const val DEFAULT_RESOURCE = "live-ai/bio-cases-v2.json"

    fun load(
        resourceName: String = DEFAULT_RESOURCE,
        classLoader: ClassLoader = BioEvalCorpusLoader::class.java.classLoader,
    ): BioEvalCorpus {
        val bytes =
            requireNotNull(classLoader.getResourceAsStream(resourceName)) {
                "Evaluation corpus resource was not found: $resourceName"
            }.use { input -> input.readBytes() }
        return parse(bytes)
    }

    internal fun parse(bytes: ByteArray): BioEvalCorpus {
        require(bytes.isNotEmpty()) { "Evaluation corpus must not be empty" }
        val root =
            try {
                JsonMapper.builder().build()
                    .reader()
                    .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .readTree(bytes)
            } catch (exception: RuntimeException) {
                throw IllegalArgumentException("Evaluation corpus is not valid JSON", exception)
            }
        root.requireObject(
            path = "root",
            expectedFields = setOf("corpus_id", "schema_version", "cases"),
        )

        val corpusId = root.requiredText("corpus_id", "root")
        val schemaVersion =
            root.get("schema_version")
                .takeIf { node -> node != null && node.isIntegralNumber }
                ?.intValue()
                ?: throw IllegalArgumentException(
                    "root.schema_version must be an integer",
                )
        val casesNode =
            root.get("cases")
                ?.takeIf(JsonNode::isArray)
                ?: throw IllegalArgumentException("root.cases must be an array")
        val cases =
            casesNode.mapIndexed { index, caseNode ->
                parseCase(caseNode, index)
            }

        return BioEvalCorpus(
            id = corpusId,
            schemaVersion = schemaVersion,
            sha256 = BioEvalHash.sha256(bytes),
            cases = cases,
        )
    }

    private fun parseCase(
        node: JsonNode,
        index: Int,
    ): BioEvalCase {
        val path = "root.cases[$index]"
        node.requireObject(
            path = path,
            expectedFields =
                setOf("id", "slices", "job_category", "interests", "hobby_count"),
        )

        val jobWireValue = node.requiredText("job_category", path)
        val job =
            SafeJobCode.entries.firstOrNull { candidate ->
                candidate.wireValue == jobWireValue
            } ?: throw IllegalArgumentException("$path.job_category is not a safe job code")

        val interests =
            node.requiredTextArray("interests", path).map { wireValue ->
                SafeInterestCode.entries.firstOrNull { candidate ->
                    candidate.wireValue == wireValue
                } ?: throw IllegalArgumentException(
                    "$path.interests contains an unknown safe interest code",
                )
            }

        val slices = node.requiredTextArray("slices", path)
        require(slices.distinct() == slices) {
            "$path.slices must not contain duplicates"
        }
        return BioEvalCase(
            id = node.requiredText("id", path),
            slices = slices.toSet(),
            jobCategory = job,
            interests = interests,
            hobbyCount = node.requiredInt("hobby_count", path),
        )
    }

    private fun JsonNode.requireObject(
        path: String,
        expectedFields: Set<String>,
    ) {
        require(isObject) { "$path must be an object" }
        val actualFields = propertyNames().asSequence().toSet()
        require(actualFields == expectedFields) {
            "$path must contain only the approved typed fields"
        }
    }

    private fun JsonNode.requiredText(
        fieldName: String,
        path: String,
    ): String =
        get(fieldName)
            ?.takeIf(JsonNode::isString)
            ?.stringValue()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$path.$fieldName must be a nonblank string")

    private fun JsonNode.requiredTextArray(
        fieldName: String,
        path: String,
    ): List<String> {
        val array =
            get(fieldName)
                ?.takeIf(JsonNode::isArray)
                ?: throw IllegalArgumentException("$path.$fieldName must be an array")
        require(!array.isEmpty) { "$path.$fieldName must not be empty" }
        return array.mapIndexed { index, value ->
            value.takeIf(JsonNode::isString)
                ?.stringValue()
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException(
                    "$path.$fieldName[$index] must be a nonblank string",
                )
        }
    }

    private fun JsonNode.requiredInt(
        fieldName: String,
        path: String,
    ): Int {
        val value = get(fieldName)
        require(value != null && value.isIntegralNumber && value.canConvertToInt()) {
            "$path.$fieldName must be an integer"
        }
        return value.intValue()
    }
}

internal object BioEvalHash {
    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
