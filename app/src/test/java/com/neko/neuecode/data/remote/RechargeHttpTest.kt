package com.neko.neuecode.data.remote

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RechargeHttpTest {
    @Test fun cancellationDuringBodyReadCancelsOnlyThatCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late portal").setBodyDelay(2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody("independent"))
            val client = OkHttpClient()
            val call = client.newCall(Request.Builder().url(server.url("/slow")).build())
            val job = launch(Dispatchers.IO) { call.readRechargePage(); fail("cancelled result must not be delivered") }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            job.cancelAndJoin()
            assertTrue(call.isCanceled())
            val other = client.newCall(Request.Builder().url(server.url("/other")).build()).readRechargePage()
            assertEquals("independent", other.body)
            assertEquals("/other", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
