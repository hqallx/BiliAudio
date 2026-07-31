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
} catch (e: kotlinx.coroutines.CancellationException) {
    // 结构化并发：协程被取消时必须重新抛出，不能吞成 Result.Error，
    // 否则 viewModelScope 取消后仍会执行 catch 块更新 StateFlow，浪费资源。
    throw e
} catch (e: Exception) {
    Result.Error(e, e.message ?: "Unknown error")
}
