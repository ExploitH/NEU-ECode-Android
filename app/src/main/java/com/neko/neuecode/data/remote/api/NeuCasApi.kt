package com.neko.neuecode.data.remote.api

import com.neko.neuecode.data.remote.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * NEU CAS (Central Authentication Service) API
 * Based on reverse engineering of 智慧东大 app
 */
interface NeuCasApi {
    
    /**
     * Get CAS login page (to extract execution token)
     */
    @GET("tpass/login")
    suspend fun getLoginPage(
        @Query("service") service: String? = null
    ): Response<ResponseBody>
    
    /**
     * Submit CAS login credentials
     */
    @FormUrlEncoded
    @POST("tpass/login")
    suspend fun submitLogin(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("lt") lt: String = "",
        @Field("execution") execution: String = "e1s1",
        @Field("_eventId") eventId: String = "submit",
        @Query("service") service: String? = null
    ): Response<ResponseBody>
    
    /**
     * Validate service ticket (CAS callback)
     */
    @GET("cas/serviceValidate")
    suspend fun validateTicket(
        @Query("ticket") ticket: String,
        @Query("service") service: String
    ): Response<ResponseBody>
}
