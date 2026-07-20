package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationDeadlineExceededException
import com.persons.finder.person.bio.BioGenerationFailure
import java.net.URI
import java.time.Duration
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

internal class OpenAiModelProviderClient(
    private val apiKey: String,
    private val model: String,
    private val timeout: Duration,
    private val objectMapper: ObjectMapper,
    private val transport: ProviderHttpTransport,
) : ModelProviderClient {
    init {
        require(apiKey.isNotBlank()) { "OpenAI API key is required" }
        require(model.isNotBlank()) { "OpenAI model is required" }
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
                objectMapper.writeValueAsString(request.toOpenAiBody())
            } catch (_: JacksonException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            } catch (_: RuntimeException) {
                return ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE)
            }
        val httpRequest =
            ProviderHttpRequest(
                uri = RESPONSES_URI,
                headers =
                    mapOf(
                        "Authorization" to "Bearer $apiKey",
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

    private fun ModelGenerationRequest.toOpenAiBody(): Map<String, Any> =
        linkedMapOf<String, Any>(
            "model" to model,
            "instructions" to instructions,
            "input" to inputJson,
            "store" to false,
            "max_output_tokens" to maxOutputTokens,
            "text" to
                mapOf(
                    "format" to
                        mapOf(
                            "type" to "json_schema",
                            "name" to "bio_template",
                            "strict" to true,
                            "schema" to objectMapper.readTree(outputSchemaJson),
                        ),
                ),
        ).apply {
            if (model == GPT_5_6_ALIAS || model.startsWith(GPT_5_6_PREFIX)) {
                put("reasoning", mapOf("effort" to "none"))
            }
        }

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
        if (body.textValue("object") != "response") {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        return when (body.textValue("status")) {
            "completed" -> extractCompletedOutput(body)
            "incomplete" -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            else -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
    }

    private fun extractCompletedOutput(body: JsonNode): ModelProviderResult {
        val output = body.path("output")
        if (!output.isArray) {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        val messages =
            output.filter { item ->
                item.textValue("type") == "message"
            }
        if (
            messages.size != 1 ||
            messages.single().textValue("role") != "assistant" ||
            messages.single().textValue("status") != "completed"
        ) {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        val content = messages.single().path("content")
        if (!content.isArray) {
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        val texts = mutableListOf<String>()
        var refused = false
        content.forEach { item ->
            when (item.textValue("type")) {
                "output_text" -> item.get("text")?.takeIf(JsonNode::isString)?.let {
                    texts += it.stringValue()
                }

                "refusal" -> refused = true
            }
        }
        return when {
            refused -> ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED)
            texts.size != 1 -> ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
            else -> ModelProviderResult.Generated(texts.single())
        }
    }

    private fun JsonNode.textValue(fieldName: String): String? =
        get(fieldName)?.takeIf(JsonNode::isString)?.stringValue()

    private companion object {
        val RESPONSES_URI: URI = URI.create("https://api.openai.com/v1/responses")
        const val GPT_5_6_ALIAS = "gpt-5.6"
        const val GPT_5_6_PREFIX = "gpt-5.6-"
    }
}
