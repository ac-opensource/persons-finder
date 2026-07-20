package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationDeadlineExceededException
import com.persons.finder.person.bio.BioGenerationFailure
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

internal class GeminiModelProviderClient(
    private val apiKey: String,
    private val model: String,
    private val timeout: Duration,
    private val objectMapper: ObjectMapper,
    private val transport: ProviderHttpTransport,
) : ModelProviderClient {
    init {
        require(apiKey.isNotBlank()) { "Gemini API key is required" }
        require(model.isNotBlank()) { "Gemini model is required" }
    }

    override fun generate(request: ModelGenerationRequest): ModelProviderResult {
        val effectiveTimeout =
            try {
                minOf(timeout, request.context.requireRemaining())
            } catch (_: BioGenerationDeadlineExceededException) {
                return ModelProviderResult.Failure(BioGenerationFailure.TIMEOUT)
            }
        val body =
            try {
                objectMapper.writeValueAsString(request.toGeminiBody())
            } catch (_: JacksonException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            } catch (_: RuntimeException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            }
        val httpRequest =
            ProviderHttpRequest(
                uri = modelUri(model),
                headers =
                    mapOf(
                        "x-goog-api-key" to apiKey,
                        "Content-Type" to "application/json",
                    ),
                body = body,
                timeout = effectiveTimeout,
            )

        return when (val call = transport.call(httpRequest)) {
            is ProviderCallResult.Failure -> ModelProviderResult.Failure(call.reason)
            is ProviderCallResult.Response -> parseResponse(call.value)
        }
    }

    private fun ModelGenerationRequest.toGeminiBody(): Map<String, Any> =
        mapOf(
            "systemInstruction" to
                mapOf(
                    "parts" to listOf(mapOf("text" to instructions)),
                ),
            "contents" to
                listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to inputJson)),
                    ),
                ),
            "generationConfig" to
                mapOf(
                    "responseMimeType" to "application/json",
                    "responseJsonSchema" to objectMapper.readTree(outputSchemaJson),
                    "candidateCount" to 1,
                    "maxOutputTokens" to maxOutputTokens,
                ),
        )

    private fun parseResponse(response: ProviderHttpResponse): ModelProviderResult {
        failureForHttpStatus(response.statusCode)?.let {
            return ModelProviderResult.Failure(it)
        }
        if (response.bodyTooLarge || response.body.length > MAX_PROVIDER_RESPONSE_BYTES) {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        val body =
            try {
                objectMapper.readTree(response.body)
            } catch (_: JacksonException) {
                return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            } catch (_: RuntimeException) {
                return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            }
        if (body.path("promptFeedback").hasNonNull("blockReason")) {
            return ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED)
        }
        val candidate = body.path("candidates").firstOrNull()
            ?: return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        return when (candidate.path("finishReason").stringValue()) {
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" ->
                ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED)

            "MAX_TOKENS" -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            "STOP" -> extractStoppedOutput(candidate)
            else -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
    }

    private fun extractStoppedOutput(candidate: JsonNode): ModelProviderResult {
        val texts =
            candidate.path("content").path("parts")
                .toList()
                .mapNotNull { part -> part.get("text")?.takeIf(JsonNode::isString)?.stringValue() }
        return if (texts.size == 1) {
            ModelProviderResult.Generated(texts.single())
        } else {
            ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
    }

    private fun modelUri(model: String): URI {
        val encoded = URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20")
        return URI.create("$GENERATE_CONTENT_BASE_URI/$encoded:generateContent")
    }

    private companion object {
        const val GENERATE_CONTENT_BASE_URI =
            "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
