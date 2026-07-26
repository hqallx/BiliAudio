package com.biliaudio.data

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Throwable, String) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.data
}

inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, e.message ?: "Unknown error")
}
