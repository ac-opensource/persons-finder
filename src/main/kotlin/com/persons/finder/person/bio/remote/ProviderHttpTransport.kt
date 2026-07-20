package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationFailure
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

internal class ProviderHttpRequest(
    val method: String = "POST",
    val uri: URI,
    val headers: Map<String, String>,
    val body: String,
    val timeout: Duration,
) {
    init {
        require(method == "POST")
        require(uri.scheme == "https")
        require(uri.userInfo == null)
        require(uri.fragment == null)
    }

    override fun toString(): String =
        "ProviderHttpRequest(method=$method, uri=$uri, timeout=$timeout)"
}

internal class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
    val bodyTooLarge: Boolean = false,
) {
    override fun toString(): String = "ProviderHttpResponse(statusCode=$statusCode)"
}

internal fun interface ProviderHttpTransport {
    fun send(request: ProviderHttpRequest): ProviderHttpResponse
}

internal class JdkProviderHttpTransport(
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
) : ProviderHttpTransport {
    override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
        val builder =
            HttpRequest.newBuilder(request.uri)
                .timeout(request.timeout)
                .method(
                    request.method,
                    HttpRequest.BodyPublishers.ofString(request.body, StandardCharsets.UTF_8),
                )
        request.headers.forEach(builder::header)
        val response =
            client.send(
                builder.build(),
                HttpResponse.BodyHandler {
                    BoundedProviderBodySubscriber(MAX_PROVIDER_RESPONSE_BYTES)
                },
            )
        val body = response.body()
        return ProviderHttpResponse(
            statusCode = response.statusCode(),
            body = (body as? BoundedProviderBody.Content)?.value.orEmpty(),
            bodyTooLarge = body is BoundedProviderBody.TooLarge,
        )
    }
}

internal sealed interface BoundedProviderBody {
    data class Content(val value: String) : BoundedProviderBody

    data object TooLarge : BoundedProviderBody
}

internal class BoundedProviderBodySubscriber(
    private val maxBytes: Int,
) : HttpResponse.BodySubscriber<BoundedProviderBody> {
    private val result = CompletableFuture<BoundedProviderBody>()
    private val buffer = ByteArrayOutputStream(minOf(maxBytes, INITIAL_RESPONSE_BUFFER_BYTES))
    private var subscription: Flow.Subscription? = null
    private var receivedBytes = 0L
    private var completed = false

    init {
        require(maxBytes > 0)
    }

    override fun getBody(): CompletionStage<BoundedProviderBody> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (this.subscription != null) {
            subscription.cancel()
            return
        }
        this.subscription = subscription
        subscription.request(1)
    }

    override fun onNext(items: List<ByteBuffer>) {
        if (completed) {
            return
        }
        val incomingBytes = items.sumOf { it.remaining().toLong() }
        if (receivedBytes + incomingBytes > maxBytes) {
            completed = true
            subscription?.cancel()
            result.complete(BoundedProviderBody.TooLarge)
            return
        }

        items.forEach { item ->
            val bytes = ByteArray(item.remaining())
            item.get(bytes)
            buffer.write(bytes)
        }
        receivedBytes += incomingBytes
        subscription?.request(1)
    }

    override fun onError(throwable: Throwable) {
        if (!completed) {
            completed = true
            result.completeExceptionally(throwable)
        }
    }

    override fun onComplete() {
        if (!completed) {
            completed = true
            result.complete(
                BoundedProviderBody.Content(
                    buffer.toString(StandardCharsets.UTF_8),
                ),
            )
        }
    }
}

internal sealed interface ProviderCallResult {
    data class Response(val value: ProviderHttpResponse) : ProviderCallResult

    data class Failure(val reason: BioGenerationFailure) : ProviderCallResult
}

internal fun ProviderHttpTransport.call(request: ProviderHttpRequest): ProviderCallResult =
    try {
        ProviderCallResult.Response(send(request))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: HttpTimeoutException) {
        ProviderCallResult.Failure(BioGenerationFailure.TIMEOUT)
    } catch (_: IOException) {
        ProviderCallResult.Failure(BioGenerationFailure.UNAVAILABLE)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CancellationException("Provider request was cancelled").apply {
            initCause(interrupted)
        }
    } catch (_: RuntimeException) {
        ProviderCallResult.Failure(BioGenerationFailure.UNAVAILABLE)
    }

internal fun failureForHttpStatus(statusCode: Int): BioGenerationFailure? =
    when {
        statusCode in 200..299 -> null
        statusCode == 408 || statusCode == 504 -> BioGenerationFailure.TIMEOUT
        statusCode == 429 -> BioGenerationFailure.RATE_LIMITED
        else -> BioGenerationFailure.UNAVAILABLE
    }

internal const val MAX_PROVIDER_RESPONSE_BYTES = 65_536
private const val INITIAL_RESPONSE_BUFFER_BYTES = 8_192
