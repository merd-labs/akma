package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The terminal callback, not coroutine cancellation, establishes when a conversation may be closed. */
internal interface NativeReplyCallbacks {
    fun onText(text: String)
    fun onComplete(error: Throwable? = null)
}

internal class NativeReplyOperation(
    private val maxRawChars: Int,
    private val onCancellationFailure: () -> Unit = {},
) {
    private val terminal = CompletableDeferred<Result<String>>()
    private val lock = Any()
    private val buffer = StringBuilder()
    private var tooLong = false
    private val callbacks = object : NativeReplyCallbacks {
        override fun onText(text: String) = synchronized(lock) {
            if (!terminal.isCompleted && !tooLong) {
                if (text.length > maxRawChars - buffer.length) {
                    tooLong = true
                    buffer.setLength(0)
                } else buffer.append(text)
            }
            Unit
        }

        override fun onComplete(error: Throwable?) = synchronized(lock) {
            if (!terminal.isCompleted) {
                terminal.complete(when {
                    error != null -> Result.failure(error)
                    tooLong -> Result.failure(IllegalStateException("Native reply exceeds the raw text limit."))
                    else -> Result.success(buffer.toString())
                })
                buffer.setLength(0)
            }
            Unit
        }
    }
    val completed: Boolean get() = terminal.isCompleted

    suspend fun await(start: (NativeReplyCallbacks) -> Unit, cancel: () -> Unit): String {
        start(callbacks)
        try {
            return terminal.await().getOrThrow()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                var requested = false
                val cancellation = try {
                    cancel()
                    requested = true
                    Result.success(Unit)
                } catch (failure: CancellationException) {
                    Result.failure(failure)
                } catch (failure: LinkageError) {
                    Result.failure(failure)
                } catch (failure: OutOfMemoryError) {
                    Result.failure(failure)
                } catch (failure: Exception) {
                    Result.failure(failure)
                } finally {
                    if (!requested) onCancellationFailure()
                }
                // Keep the worker's engine mutex until native completion. A hung JNI operation requires process restart.
                val completion = terminal.await()
                val failure = completion.exceptionOrNull()
                if (failure != null && failure !is CancellationException) throw failure
                cancellation.getOrThrow()
            }
            throw cancelled
        }
    }
}
