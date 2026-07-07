package com.neko.neuecode.domain.model

/**
 * User domain model
 */
data class User(
    val userId: String,
    val username: String,
    val name: String,
    val studentId: String? = null,
    val department: String? = null,
    val avatar: String? = null,
    val email: String? = null
)

/**
 * Balance information
 */
data class Balance(
    val cardBalance: String,
    val networkBalance: String,
    val lastUpdate: Long = System.currentTimeMillis()
)

/**
 * Session state sealed class
 */
sealed class SessionState {
    object Idle : SessionState()
    object Loading : SessionState()
    data class Authenticated(
        val user: User,
        val lastRefresh: Long,
        val expiresAt: Long
    ) : SessionState()
    data class Error(val message: String, val needRelogin: Boolean = false) : SessionState()
    object Expired : SessionState()
}

/**
 * Result wrapper for operations
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}
