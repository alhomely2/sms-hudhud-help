package com.hudhud.app.util

import android.content.Context
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sealed class for representing UI state with loading, success, and error states.
 * Used for UI binding with ViewModels.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorMessageOrNull(): String? = (this as? Error)?.message
}

/**
 * Extension to convert Flow to Resource Flow
 */
fun <T> Flow<T>.asResource(): Flow<Resource<T>> = kotlinx.coroutines.flow.flow {
    emit(Resource.Loading)
    collect { emit(Resource.Success(it)) }
}.catch { emit(Resource.Error(it.message ?: "Unknown error", it)) }
.flowOn(Dispatchers.IO)

/**
 * Extension to handle Resource in UI
 */
inline fun <T> Resource<T>.onSuccess(action: (T) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}

inline fun <T> Resource<T>.onError(action: (String) -> Unit): Resource<T> {
    if (this is Resource.Error) action(message)
    return this
}

inline fun <T> Resource<T>.onLoading(action: () -> Unit): Resource<T> {
    if (this is Resource.Loading) action()
    return this
}

/**
 * Show error message to user
 */
fun Resource<*>.showError(context: Context, scope: CoroutineScope? = null) {
    errorMessageOrNull()?.let { message ->
        if (scope != null) {
            CoroutineScope(scope.coroutineContext).launch {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Show snackbar for error
 */
fun Resource<*>.showErrorSnackbar(
    view: android.view.View,
    scope: CoroutineScope,
    duration: Int = Snackbar.LENGTH_LONG,
) {
    errorMessageOrNull()?.let { message ->
        CoroutineScope(scope.coroutineContext).launch {
            Snackbar.make(view, message, duration).show()
        }
    }
}

/**
 * Wrap suspend function in Resource
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error", e)
        }
    }
}

/**
 * Wrap suspend function in Resource with mapper
 */
suspend fun <T, R> safeApiCall(
    apiCall: suspend () -> T,
    mapper: (T) -> R,
): Resource<R> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(mapper(apiCall()))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error", e)
        }
    }
}

/**
 * Data class for paginated results
 */
data class PagedResource<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean,
) {
    val isFirstPage: Boolean get() = page == 0
    val isLastPage: Boolean get() = !hasMore
}

/**
 * Extension to convert list to paged resource
 */
fun <T> List<T>.toPaged(page: Int, pageSize: Int): PagedResource<T> {
    val startIndex = page * pageSize
    val endIndex = minOf(startIndex + pageSize, size)
    
    return if (startIndex >= size) {
        PagedResource(emptyList(), page, (size / pageSize) + 1, false)
    } else {
        PagedResource(
            items = subList(startIndex, endIndex),
            page = page,
            totalPages = (size / pageSize) + 1,
            hasMore = endIndex < size
        )
    }
}