package com.persons.finder.person.bio.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.Flow

class ProviderHttpTransportTest {
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
