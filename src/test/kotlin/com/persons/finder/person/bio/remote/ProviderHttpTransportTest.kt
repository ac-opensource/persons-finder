package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.Flow

class ProviderHttpTransportTest {
    @Test
    fun `provider HTTP status classifier covers success and normalized failures`() {
        mapOf(
            200 to null,
            299 to null,
            408 to BioGenerationFailure.TIMEOUT,
            504 to BioGenerationFailure.TIMEOUT,
            429 to BioGenerationFailure.RATE_LIMITED,
            400 to BioGenerationFailure.UNAVAILABLE,
            500 to BioGenerationFailure.UNAVAILABLE,
        ).forEach { (statusCode, expected) ->
            assertEquals(expected, failureForHttpStatus(statusCode), "HTTP $statusCode")
        }
    }

    @Test
    fun `provider transport normalizes timeout IO and unexpected runtime failures`() {
        listOf(
            HttpTimeoutException("synthetic timeout") to BioGenerationFailure.TIMEOUT,
            IOException("synthetic IO failure") to BioGenerationFailure.UNAVAILABLE,
            IllegalStateException("synthetic runtime failure") to BioGenerationFailure.UNAVAILABLE,
        ).forEach { (thrown, expected) ->
            val result =
                ProviderHttpTransport {
                    throw thrown
                }.call(providerRequest())

            assertEquals(ProviderCallResult.Failure(expected), result, thrown::class.simpleName)
        }
    }

    @Test
    fun `interrupted provider transport propagates cancellation and preserves interrupt flag`() {
        val interrupted = InterruptedException("synthetic interruption")

        try {
            val cancellation =
                assertThrows(CancellationException::class.java) {
                    ProviderHttpTransport {
                        throw interrupted
                    }.call(providerRequest())
                }

            assertEquals(interrupted, cancellation.cause)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `provider response subscriber accepts the byte ceiling`() {
        val subscriber = BoundedProviderBodySubscriber(MAX_PROVIDER_RESPONSE_BYTES)
        val subscription = RecordingSubscription()
        subscriber.onSubscribe(subscription)
        val exact = "x".repeat(MAX_PROVIDER_RESPONSE_BYTES)
        subscriber.onNext(listOf(ByteBuffer.wrap(exact.toByteArray())))
        subscriber.onComplete()

        assertEquals(
            BoundedProviderBody.Content(exact),
            subscriber.body.toCompletableFuture().join(),
        )
        assertEquals(2, subscription.requests)
        assertTrue(!subscription.cancelled)
    }

    @Test
    fun `provider response subscriber cancels before buffering beyond the byte ceiling`() {
        val subscriber = BoundedProviderBodySubscriber(MAX_PROVIDER_RESPONSE_BYTES)
        val subscription = RecordingSubscription()
        subscriber.onSubscribe(subscription)
        subscriber.onNext(
            listOf(
                ByteBuffer.wrap("x".repeat(MAX_PROVIDER_RESPONSE_BYTES).toByteArray()),
                ByteBuffer.wrap(byteArrayOf(0)),
            ),
        )

        assertEquals(
            BoundedProviderBody.TooLarge,
            subscriber.body.toCompletableFuture().join(),
        )
        assertTrue(subscription.cancelled)
        assertEquals(1, subscription.requests)
    }

    private fun providerRequest() =
        ProviderHttpRequest(
            uri = URI.create("https://example.test/provider"),
            headers = emptyMap(),
            body = "{}",
            timeout = Duration.ofSeconds(1),
        )

    private class RecordingSubscription : Flow.Subscription {
        var requests = 0
        var cancelled = false

        override fun request(n: Long) {
            requests++
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
