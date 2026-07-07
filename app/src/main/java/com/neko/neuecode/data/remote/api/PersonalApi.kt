package com.neko.neuecode.data.remote.api

import com.neko.neuecode.data.remote.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * NEU Personal Portal API
 * Based on reverse engineering of 智慧东大 app
 */
interface PersonalApi {
    
    /**
     * App login check (Step 1)
     * Validates credentials and returns ticket
     */
    @POST("portal/manage/common/app_login/check")
    @FormUrlEncoded
    suspend fun appLoginCheck(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("type") type: String = "password",
        @Field("client") client: String = "android",
        @Field("version") version: String = "4.0.0"
    ): Response<AppLoginResponse<LoginCheckData>>
    
    /**
     * App login commit (Step 2)
     * Commits login with ticket and establishes session
     */
    /**
     * App login commit - RSA encrypted protocol
     * 
     * Based on reverse engineering of 智慧东大 v3.1.5:
     * - content: RSA-1024 encrypted JSON (username, password, imei, mobile_type)
     * - authorization-str: RSA-1024 encrypted JSON (timestamp, ticket)
     */
    @POST("portal/manage/common/app_login/commit")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun appLoginCommit(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<AppLoginResponse<LoginCommitData>>

    @POST("portal/manage/common/app_login/smsSend")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun appLoginSmsSend(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<AppLoginResponse<LoginCommitData>>

    @POST("portal/manage/common/app_login/verifyCommit")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun appLoginVerifyCommit(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<AppLoginResponse<LoginCommitData>>
    
    /**
     * Get personal info
     */
    @GET("portal/personal/frontend/data/info_app")
    suspend fun getPersonalInfo(): Response<PersonalInfoResponse>
    
    /**
     * Legacy campus card balance endpoint. Kept only for compatibility; current
     * portal returns 404 here.
     */
    @GET("portal/neu/frontend/ykt/balance")
    suspend fun getBalance(): Response<BalanceResponse>

    /**
     * Structured personal-data cards shown by 一号通.
     */
    @POST("portal/personal/frontend/data/items_app")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun getPersonalDataItems(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<ResponseBody>

    /**
     * Detail payload for a specific personal-data card.
     */
    @POST("portal/personal/frontend/data/detail_app")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun getPersonalDataDetail(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<ResponseBody>
    
    /**
     * Check login status for the RSA app protocol.
     * Official Flutter logic calls this with the saved neu_login_ticket.
     */
    @POST("portal/manage/common/app_login/check")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun appLoginTicketCheck(
        @Body content: okhttp3.RequestBody,
        @Header("authorization-str") authorizationStr: String,
        @Header("from-eai") fromEai: String = "1",
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept-Language") acceptLanguage: String = "zh-Hans-CN;q=1, en-CN;q=0.9",
        @Header("User-Agent") userAgent: String = "ZhilinEai ZhilinNeuApp/3.1.1"
    ): Response<ResponseBody>

    /**
     * Check login status (legacy)
     */
    @GET("portal/manage/common/app_login/check")
    suspend fun checkLogin(): Response<AppLoginResponse<LoginCheckData>>
    
    /**
     * Logout
     */
    @POST("portal/manage/common/app_login/logout")
    suspend fun logout(): Response<ResponseBody>
}
