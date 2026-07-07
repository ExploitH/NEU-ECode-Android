package com.neko.neuecode.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.remote.api.PersonalApi
import com.neko.neuecode.data.remote.crypto.NeuRsaCrypto
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.domain.model.Result
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Personal data repository
 * Handles balance queries and other personal information
 */
@Singleton
class PersonalRepository @Inject constructor(
    private val personalApi: PersonalApi,
    private val userPreferences: UserPreferences,
    private val rsaCrypto: NeuRsaCrypto,
    private val authRepository: AuthRepository
) {

    /**
     * Get campus card and network balance from 一号通 app structured personal-data API.
     */
    suspend fun getBalance(): Result<Balance> {
        var refreshed = false
        repeat(2) { attempt ->
            when (val result = getBalanceOnce()) {
                is Result.Success -> return result
                is Result.Error -> {
                    if (!refreshed && result.isSessionExpiredLike()) {
                        refreshed = true
                        Timber.i("Balance API indicates expired session; attempting auto refresh before retry")
                        when (val refresh = authRepository.ensureFreshSession()) {
                            is Result.Success -> Unit
                            is Result.Error -> return Result.Error(
                                refresh.exception,
                                refresh.message ?: result.message ?: "登录已过期，请重新登录"
                            )
                            else -> return result
                        }
                    } else {
                        if (attempt > 0) Timber.w("Balance retry failed: ${result.message}")
                        return result
                    }
                }
                else -> Unit
            }
        }
        return Result.Error(Exception("Balance retry exhausted"), "获取余额失败")
    }

    private suspend fun getBalanceOnce(): Result<Balance> {
        return try {
            Timber.d("Fetching balance from personal-data app API...")

            val itemsResponse = personalApi.getPersonalDataItems(
                content = encryptedFormBody(mapOf("type" to "personal_data")),
                authorizationStr = encryptedAuthorization()
            )
            if (!itemsResponse.isSuccessful) {
                Timber.e("Personal data items_app fetch failed: ${itemsResponse.code()}")
                return Result.Error(
                    Exception("Failed to fetch balance entry list HTTP ${itemsResponse.code()}"),
                    if (itemsResponse.code() == 401 || itemsResponse.code() == 403) "登录已过期" else "获取余额入口失败"
                )
            }

            val itemsJson = itemsResponse.body()?.string().orEmpty()
            val root = JsonParser.parseString(itemsJson)
            val errorCode = root.findFirstString("e") ?: root.findFirstString("code")
            if (errorCode != null && errorCode != "0") {
                val message = root.findFirstString("m") ?: root.findFirstString("message") ?: "会话无效"
                Timber.w("Personal data items_app returned error: $errorCode $message")
                return Result.Error(Exception(message), message)
            }

            val payload = root.unwrapEncryptedPayload() ?: root
            val cardId = payload.findObjectWithKey("card.balance")?.findFirstString("id")
            val netId = payload.findObjectWithKey("net.balance")?.findFirstString("id")

            val cardBalance = cardId?.let { fetchDetailValue(it) }.orEmpty()
            val networkBalance = netId?.let { fetchDetailValue(it) }.orEmpty()

            if (cardBalance.isBlank() && networkBalance.isBlank()) {
                Timber.w("No valid balance card ids found in personal data items_app")
                return Result.Error(Exception("No balance data"), "未找到有效余额数据")
            }

            val balance = Balance(
                cardBalance = cardBalance,
                networkBalance = networkBalance,
                lastUpdate = System.currentTimeMillis()
            )

            Timber.d("Balance fetched from app structured API")
            Result.Success(balance)
        } catch (e: Exception) {
            Timber.e(e, "Balance fetch exception")
            Result.Error(e, "获取余额失败: ${e.message}")
        }
    }

    private fun Result.Error.isSessionExpiredLike(): Boolean {
        val text = listOfNotNull(message, exception.message).joinToString(" ")
        return text.contains("请先登录") ||
                text.contains("登录已过期") ||
                text.contains("会话无效") ||
                text.contains("ticket", ignoreCase = true) ||
                text.contains("401") ||
                text.contains("403")
    }

    private suspend fun fetchDetailValue(id: String): String? {
        val response = personalApi.getPersonalDataDetail(
            content = encryptedFormBody(mapOf("id" to id)),
            authorizationStr = encryptedAuthorization()
        )
        if (!response.isSuccessful) {
            Timber.w("Personal data detail_app $id failed: ${response.code()}")
            return null
        }

        val root = JsonParser.parseString(response.body()?.string().orEmpty())
        val errorCode = root.findFirstString("e") ?: root.findFirstString("code")
        if (errorCode != null && errorCode != "0") {
            Timber.w("Personal data detail_app $id returned error: $errorCode")
            return null
        }

        val data = root.unwrapEncryptedPayload()
            ?: root.findFirstObject("d")
            ?: root.findFirstObject("data")
            ?: root
        val value = data.findFirstString("value")
            ?: data.findFirstString("balance")
            ?: data.findFirstString("amount")
            ?: data.findFirstString("money")
        val unit = data.findFirstString("unit").orEmpty()
        return value?.takeIf { it.isNotBlank() }?.let { if (unit.isNotBlank()) "$it$unit" else it }
    }

    private suspend fun encryptedAuthorization(): String {
        return rsaCrypto.encryptAuthorizationStr(ticket = userPreferences.getLoginTicket().orEmpty())
    }

    private fun encryptedFormBody(values: Map<String, String>): RequestBody {
        val encryptedContent = rsaCrypto.encryptContentMap(values)
        val urlEncodedContent = URLEncoder.encode(encryptedContent, "UTF-8")
        return RequestBody.create(
            "application/x-www-form-urlencoded".toMediaType(),
            "content=$urlEncodedContent"
        )
    }

    private fun JsonElement.findFirstString(name: String): String? {
        if (isJsonObject) {
            val obj = asJsonObject
            obj.get(name)?.let { element ->
                if (element.isJsonPrimitive) return element.asString
            }
            obj.entrySet().forEach { (_, value) ->
                value.findFirstString(name)?.let { return it }
            }
        } else if (isJsonArray) {
            asJsonArray.forEach { element ->
                element.findFirstString(name)?.let { return it }
            }
        }
        return null
    }

    private fun JsonElement.findFirstObject(name: String): JsonElement? {
        if (isJsonObject) {
            val obj = asJsonObject
            obj.get(name)?.takeIf { it.isJsonObject }?.let { return it }
            obj.entrySet().forEach { (_, value) ->
                value.findFirstObject(name)?.let { return it }
            }
        } else if (isJsonArray) {
            asJsonArray.forEach { element ->
                element.findFirstObject(name)?.let { return it }
            }
        }
        return null
    }

    private fun JsonElement.findObjectWithKey(key: String): JsonElement? {
        if (isJsonObject) {
            val obj = asJsonObject
            obj.get("key")?.takeIf { it.isJsonPrimitive && it.asString == key }?.let { return this }
            obj.entrySet().forEach { (_, value) ->
                value.findObjectWithKey(key)?.let { return it }
            }
        } else if (isJsonArray) {
            asJsonArray.forEach { element ->
                element.findObjectWithKey(key)?.let { return it }
            }
        }
        return null
    }

    private fun JsonElement.unwrapEncryptedPayload(): JsonElement? {
        if (!isJsonObject) return null
        val d = asJsonObject.get("d") ?: return null
        if (!d.isJsonPrimitive || !d.asJsonPrimitive.isString) return null
        val decrypted = rsaCrypto.decryptResponse(d.asString) ?: return null
        return try {
            JsonParser.parseString(decrypted)
        } catch (_: Exception) {
            null
        }
    }
}
