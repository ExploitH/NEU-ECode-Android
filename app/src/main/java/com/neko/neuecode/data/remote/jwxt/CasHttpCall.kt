package com.neko.neuecode.data.remote.jwxt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A fully consumed response. Deliberately omit a body-bearing toString (CAS HTML is sensitive). */
class CasHttpResponse(
    val url: HttpUrl,
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

internal fun Response.readCasBody(): CasHttpResponse = use {
    CasHttpResponse(request.url, code, body?.string().orEmpty())
}

/**
 * Await headers AND the complete body, cancelling only this call. A shorter client timeout is
 * retained; otherwise the whole call (including body reads/redirects) is capped at 30 seconds.
 * On cancellation, do not return until the callback has closed its body and finished: callers
 * may hold a session mutex protecting a CookieJar shared with the next operation.
 */
suspend fun Call.awaitBody(): CasHttpResponse {
    val maxTimeoutNanos = TimeUnit.SECONDS.toNanos(30)
    val configuredTimeoutNanos = timeout().timeoutNanos()
    if (configuredTimeoutNanos == 0L || configuredTimeoutNanos > maxTimeoutNanos) {
        timeout().timeout(maxTimeoutNanos, TimeUnit.NANOSECONDS)
    }
    val finished = CompletableDeferred<Unit>()
    try {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            if (!continuation.isActive) {
                finished.complete(Unit)
                return@suspendCancellableCoroutine
            }
            try {
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        try {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            finished.complete(Unit)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val snapshot = response.use {
                                if (!continuation.isActive) return
                                CasHttpResponse(it.request.url, it.code, it.body?.string().orEmpty())
                            }
                            if (continuation.isActive) continuation.resume(snapshot)
                        } catch (e: Exception) {
                            // Cancellation may cause a socket/body exception after the caller left.
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            finished.complete(Unit)
                        }
                    }
                })
            } catch (e: Exception) {
                finished.complete(Unit)
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    } finally {
        withContext(NonCancellable) { finished.await() }
    }
}
