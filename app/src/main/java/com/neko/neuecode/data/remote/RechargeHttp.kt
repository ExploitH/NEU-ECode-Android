package com.neko.neuecode.data.remote

import com.neko.neuecode.data.remote.jwxt.CasHttpResponse
import com.neko.neuecode.data.remote.jwxt.awaitBody
import okhttp3.Call

/** Reuse the CAS cancellation contract, including body cleanup before releasing the session lock. */
internal suspend fun Call.readRechargePage(): CasHttpResponse = awaitBody()
