package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationDeadlineExceededException
import com.persons.finder.person.bio.BioGenerationFailure
import java.net.URI
import java.time.Duration
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

internal class AnthropicModelProviderClient(
    private val apiKey: String,
    private val model: String,
    private val timeout: Duration,
    private val objectMapper: ObjectMapper,
    private val transport: ProviderHttpTransport,
) : ModelProviderClient {
    init {
        require(apiKey.isNotBlank()) { "Anthropic API key is required" }
        require(model.isNotBlank()) { "Anthropic model is required" }
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
                objectMapper.writeValueAsString(request.toAnthropicBody())
            } catch (_: JacksonException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            } catch (_: RuntimeException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            }
        val httpRequest =
            ProviderHttpRequest(
                uri = MESSAGES_URI,
                headers =
                    mapOf(
                        "x-api-key" to apiKey,
                        "anthropic-version" to API_VERSION,
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

    private fun ModelGenerationRequest.toAnthropicBody(): Map<String, Any> =
        mapOf(
            "model" to model,
            "max_tokens" to maxOutputTokens,
            "system" to instructions,
            "messages" to
                listOf(
                    mapOf(
                        "role" to "user",
                        "content" to inputJson,
                    ),
                ),
            "output_config" to
                mapOf(
                    "format" to
                        mapOf(
                            "type" to "json_schema",
                            "schema" to objectMapper.readTree(outputSchemaJson),
                        ),
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
        if (
            body.path("type").stringValue() != "message" ||
            body.path("role").stringValue() != "assistant"
        ) {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        return when (body.path("stop_reason").stringValue()) {
            "end_turn" -> extractCompletedOutput(body)
            "refusal" -> ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED)
            "max_tokens" -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            else -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
    }

    private fun extractCompletedOutput(body: JsonNode): ModelProviderResult {
        val texts =
            body.path("content").toList()
                .filter { it.path("type").stringValue() == "text" }
                .mapNotNull { it.get("text")?.takeIf(JsonNode::isString)?.stringValue() }
        return if (texts.size == 1) {
            ModelProviderResult.Generated(texts.single())
        } else {
            ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
    }

    private companion object {
        const val API_VERSION = "2023-06-01"
        val MESSAGES_URI: URI = URI.create("https://api.anthropic.com/v1/messages")
    }
}
