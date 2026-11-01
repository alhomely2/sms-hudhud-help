package com.hudhud.app.data.remote

/**
 * Sealed class representing the result of an API operation.
 * Provides type-safe error handling.
 */
sealed class HudhudResult<out T> {
    
    /**
     * Success case with data
     */
    data class Success<T>(val data: T) : HudhudResult<T>()
    
    /**
     * Failure case with error
     */
    data class Error(
        val code: ErrorCode,
        val message: String,
        val details: String? = null,
    ) : HudhudResult<Nothing>()
    
    /**
     * Loading state
     */
    data object Loading : HudhudResult<Nothing>()

    /**
     * Map success data to another type
     */
    fun <R> map(transform: (T) -> R): HudhudResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    /**
     * Get data or null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Get data or default
     */
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    /**
     * Check if result is successful
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Check if result is error
     */
    val isError: Boolean get() = this is Error

    /**
     * Check if result is loading
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * Get error code
     */
    val errorCode: ErrorCode? get() = (this as? Error)?.code

    /**
     * Get error message
     */
    val errorMessage: String? get() = (this as? Error)?.message

    /**
     * Execute action on success
     */
    inline fun onSuccess(action: (T) -> Unit): HudhudResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute action on error
     */
    inline fun onError(action: (Error) -> Unit): HudhudResult<T> {
        if (this is Error) action(this)
        return this
    }

    /**
     * Error codes specific to Hudhud API
     */
    enum class ErrorCode {
        // Network errors
        NETWORK_ERROR,
        TIMEOUT,
        NO_INTERNET,
        
        // Authentication errors
        AUTH_MISSING_KEY,
        AUTH_INVALID_KEY,
        AUTH_HASH_MISMATCH,
        AUTH_FORBIDDEN,
        
        // API errors
        API_NOT_FOUND,
        API_INTERNAL_ERROR,
        API_INVALID_RESPONSE,
        API_RATE_LIMITED,
        
        // Data errors
        INVALID_FILE,
        FILE_TOO_LARGE,
        UNSUPPORTED_FORMAT,
        
        // Business logic errors
        NO_PHONE_COLUMN,
        NO_TEMPLATE,
        EMPTY_BATCH,
        VALIDATION_FAILED,
        
        // Unknown errors
        UNKNOWN,
    }

    companion object {
        /**
         * Create success result
         */
        fun <T> success(data: T): HudhudResult<T> = Success(data)

        /**
         * Create error result
         */
        fun error(code: ErrorCode, message: String, details: String? = null): HudhudResult<Nothing> =
            Error(code, message, details)

        /**
         * Create loading result
         */
        fun loading(): HudhudResult<Nothing> = Loading
    }
}

/**
 * Extension to convert nullable to result
 */
fun <T> T?.toResult(errorCode: HudhudResult.ErrorCode, errorMessage: String): HudhudResult<T> =
    if (this != null) HudhudResult.success(this)
    else HudhudResult.error(errorCode, errorMessage)

/**
 * Extension to convert exception to result
 */
fun Exception.toHudhudResult(): HudhudResult<Nothing> = when (this) {
    is java.net.UnknownHostException -> 
        HudhudResult.error(HudhudResult.ErrorCode.NO_INTERNET, "لا يوجد اتصال بالإنترنت")
    is java.net.SocketTimeoutException ->
        HudhudResult.error(HudhudResult.ErrorCode.TIMEOUT, "انتهت مهلة الاتصال")
    is java.io.IOException ->
        HudhudResult.error(HudhudResult.ErrorCode.NETWORK_ERROR, "خطأ في الشبكة: ${message}")
    else ->
        HudhudResult.error(HudhudResult.ErrorCode.UNKNOWN, "خطأ غير متوقع: ${message}")
}