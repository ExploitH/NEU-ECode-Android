package com.neko.neuecode.data.remote.jwxt

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CasHttpCallTest {
    @Test
    fun cancellingOneCallDoesNotCancelUnrelatedCallOnSharedDispatcher() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abandoned").setBodyDelay(2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody("unrelated").setBodyDelay(250, TimeUnit.MILLISECONDS))
            val probe = CasBodyProbe("/abandoned")
            val client = OkHttpClient.Builder().addNetworkInterceptor(probe.interceptor).build()
            val abandoned = client.newCall(Request.Builder().url(server.url("/abandoned")).build())
            val other = client.newCall(Request.Builder().url(server.url("/other")).build())
            val job = launch(Dispatchers.IO) { abandoned.awaitBody(); fail("late result escaped cancellation") }
            try {
                assertTrue(probe.readEntered.await(2, TimeUnit.SECONDS))
                assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                val otherResult = async(Dispatchers.IO) { other.awaitBody() }
                assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                withTimeout(1_500) { job.cancelAndJoin() }
                assertTrue(abandoned.isCanceled())
                assertFalse(other.isCanceled())
                assertEquals("unrelated", withTimeout(1_500) { otherResult.await() }.body)
                assertEquals(0L, probe.closed.count)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun cancellationWaitsForBodyCleanupBeforeReleasingSessionMutex() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("delayed").setBodyDelay(2, TimeUnit.SECONDS))
            val closeEntered = CountDownLatch(1)
            val allowClose = CountDownLatch(1)
            val probe = CasBodyProbe("/slow", beforeClose = {
                closeEntered.countDown()
                check(allowClose.await(3, TimeUnit.SECONDS)) { "test did not release close" }
            })
            val client = OkHttpClient.Builder().addNetworkInterceptor(probe.interceptor).build()
            val gate = Mutex()
            val job = launch(Dispatchers.IO) {
                gate.withLock {
                    client.newCall(Request.Builder().url(server.url("/slow")).build()).awaitBody()
                }
            }
            try {
                assertTrue(probe.readEntered.await(2, TimeUnit.SECONDS))
                job.cancel()
                assertTrue(closeEntered.await(1, TimeUnit.SECONDS))
                delay(50) // Give the cancelled caller a chance to incorrectly release its mutex.
                assertFalse("cleanup is still blocked", job.isCompleted)
                assertTrue("next session must not enter before callback exits", gate.isLocked)
                allowClose.countDown()
                withTimeout(1_500) { job.join() }
                assertFalse(gate.isLocked)
                assertEquals(0L, probe.closed.count)
            } finally {
                allowClose.countDown()
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun alreadyCancelledCoroutineNeverEnqueuesRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("must not be requested"))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/unused")).build())
            val cancelled = Job().apply { cancel() }
            val job = launch(cancelled, start = CoroutineStart.UNDISPATCHED) {
                call.awaitBody()
                fail("cancelled continuation resumed")
            }
            withTimeout(1_000) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue(call.isCanceled())
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun cancellationAfterBodyCompletedDoesNotDeliverLateSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("completed response"))
            val owner = AtomicReference<Job>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callEnd(call: Call) { owner.get().cancel() }
            }).build()
            val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                client.newCall(Request.Builder().url(server.url("/done")).build()).awaitBody()
                fail("late success must not resume the caller")
            }
            owner.set(job)
            job.start()
            withTimeout(1_500) { job.join() }
            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun cancellationBeforeHeadersDoesNotDeliverLateIoFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("delayed headers").setHeadersDelay(2, TimeUnit.SECONDS))
            val failure = AtomicReference<Throwable>()
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/slow")).build())
            val job = launch(Dispatchers.IO) {
                try {
                    call.awaitBody()
                    fail("cancelled call completed")
                } catch (e: IOException) {
                    failure.set(e)
                }
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(1_500) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
            assertNull("caller should receive cancellation, not a late socket error", failure.get())
        }
    }

    @Test
    fun realSlowBodyHonorsShorterConfiguredCallTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("delayed body").setBodyDelay(2, TimeUnit.SECONDS))
            val probe = CasBodyProbe("/slow")
            val client = OkHttpClient.Builder()
                .callTimeout(400, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(probe.interceptor)
                .build()
            val call = client.newCall(Request.Builder().url(server.url("/slow")).build())
            try {
                withTimeout(1_500) { call.awaitBody() }
                fail("expected real transport timeout")
            } catch (_: IOException) {
                assertEquals(0L, probe.readEntered.count)
                assertEquals(0L, probe.closed.count)
                assertTrue(call.isCanceled())
            }
            assertEquals(TimeUnit.MILLISECONDS.toNanos(400), call.timeout().timeoutNanos())
        }
    }

    @Test
    fun defaultAndLongCallTimeoutsAreCappedAtThirtySeconds() = runBlocking {
        MockWebServer().use { server ->
            for (timeoutSeconds in listOf(0L, 60L)) {
                server.enqueue(MockResponse().setBody("ok"))
                val client = OkHttpClient.Builder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
                val call = client.newCall(Request.Builder().url(server.url("/ok")).build())
                val result = call.awaitBody()
                assertEquals(TimeUnit.SECONDS.toNanos(30), call.timeout().timeoutNanos())
                assertEquals("ok", result.body)
                assertEquals(200, result.code)
                assertEquals(server.url("/ok"), result.url)
                assertTrue(result.isSuccessful)
            }
        }
    }
}
