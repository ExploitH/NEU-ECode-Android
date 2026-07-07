package com.neko.neuecode.data.remote.model

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Custom deserializer for AppLoginResponse that handles encrypted string or plain JSON for 'd' field
 */
class AppLoginResponseDeserializer<T> : JsonDeserializer<AppLoginResponse<T>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): AppLoginResponse<T> {
        val jsonObject = json.asJsonObject
        
        // Note: "e" is a number (0 or 1), not a string
        val code = jsonObject.get("e")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) {
                it.asInt.toString()
            } else {
                it.asString
            }
        } ?: "1"
        val message = jsonObject.get("m")?.asString ?: ""
        
        // Handle 'd' field - can be:
        // 1. Encrypted Base64 string (needs decryption) -> return as String
        // 2. Plain JSON object -> return as JsonObject
        // 3. Empty array [] -> return null
        // 4. null -> return null
        val dataElement = jsonObject.get("d")
        
        val dataRaw: Any? = when {
            dataElement == null -> null
            dataElement.isJsonNull -> null
            dataElement.isJsonArray && dataElement.asJsonArray.size() == 0 -> null
            dataElement.isJsonPrimitive && dataElement.asJsonPrimitive.isString -> {
                // It's an encrypted Base64 string - return as-is
                dataElement.asString
            }
            dataElement.isJsonObject -> {
                // It's a plain JSON object - return the JsonObject
                dataElement.asJsonObject
            }
            else -> null
        }
        
        // Create AppLoginResponse with the raw data
        // Note: AppLoginResponse constructor takes (code, message, dataRaw: Any?)
        @Suppress("UNCHECKED_CAST")
        return AppLoginResponse(code, message, dataRaw as? T)
    }
}

/**
 * Generic App Login Response (智慧东大格式)
 * e = "0" 成功, "1" 失败
 * d 可以是对象或空数组 []
 */
/**
 * App Login Response (智慧东大加密协议)
 * 
 * Response format:
 * {
 *   "e": "0",
 *   "m": "操作成功",
 *   "d": "<Base64 encrypted data or plain JSON>"
 * }
 * 
 * The "d" field can be either:
 * - Plain JSON object (when decrypted by server)
 * - Base64-encoded RSA-encrypted data (640 bytes = 5x128, needs decryption)
 */
data class AppLoginResponse<T>(
    @SerializedName("e")
    val code: String,  // "0" = success, "1" = error
    
    @SerializedName("m")
    val message: String,
    
    @SerializedName("d")
    val dataRaw: Any? = null  // Can be String (encrypted) or JsonObject (plain)
) {
    // Helper to check if data is encrypted
    val isDataEncrypted: Boolean
        get() = dataRaw is String
}

/**
 * App Login Check Response Data
 */
data class LoginCheckData(
    @SerializedName("ticket")
    val ticket: String?,
    
    @SerializedName("needVerify")
    val needVerify: Boolean? = false,
    
    @SerializedName("success")
    val success: Boolean? = false,
    
    // Legacy fields for compatibility
    @SerializedName("isLogin")
    val isLogin: Boolean? = null,
    
    @SerializedName("userId")
    val userId: String? = null,
    
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("name")
    val name: String? = null
)

/**
 * App Login Commit Response Data
 */
data class LoginCommitData(
    @SerializedName("login_code")
    val loginCode: String?,
    
    @SerializedName("login_result")
    val loginResult: String?,  // TGT ticket
    
    @SerializedName("login_ticket")
    val loginTicket: String?,  // ST ticket
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("avatar")
    val avatar: String?,
    
    @SerializedName("xgh")
    val xgh: String?,  // 学工号 (student/employee ID)
    
    @SerializedName("role_type")
    val roleType: String?,
    
    @SerializedName("role")
    val role: String?,
    
    @SerializedName("depart")
    val depart: String?,  // 部门
    
    @SerializedName("CK_LC")
    val ckLc: String?,  // Cookie
    
    @SerializedName("CK_VL")
    val ckVl: String?  // Cookie
)

// Personal Info Response
data class PersonalInfoResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: PersonalInfoData?
)

data class PersonalInfoData(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("studentId")
    val studentId: String?,
    @SerializedName("department")
    val department: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("email")
    val email: String?
)

// Balance Response
data class BalanceResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: BalanceData?
)

data class BalanceData(
    @SerializedName("balance")
    val balance: String?,
    @SerializedName("updateTime")
    val updateTime: String?
)
