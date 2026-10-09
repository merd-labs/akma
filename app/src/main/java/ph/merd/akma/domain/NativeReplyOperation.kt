package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The terminal callback, not coroutine cancellation, establishes when a conversation may be closed. */
internal interface NativeReplyCallbacks {
    fun onText(text: String)
    /** A callback conversion failure requests cancellation; it is not a native terminal event. */
    fun onFailure(error: Throwable)
    fun onComplete(error: Throwable? = null)
}

internal class NativeReplyOperation(
    private val maxRawChars: Int,
    private val onCancellationFailure: () -> Unit = {},
    private val cancellationCleanupMillis: Long = 5_000,
) {
    init { require(maxRawChars > 0 && cancellationCleanupMillis > 0) }
    private val terminal = CompletableDeferred<Result<String>>()
    private val rejected = CompletableDeferred<Throwable>()
    private val lock = Any()
    private val buffer = StringBuilder()
    private var callbackFailure: Throwable? = null
    private var abandoned = false
    private val callbacks = object : NativeReplyCallbacks {
        override fun onText(text: String) = synchronized(lock) {
            if (!abandoned && !terminal.isCompleted && callbackFailure == null) {
                if (text.length > maxRawChars - buffer.length) {
                    reject(IllegalStateException("Native reply exceeds the raw text limit."))
                } else buffer.append(text)
            }
            Unit
        }

        override fun onFailure(error: Throwable) = synchronized(lock) {
            if (!abandoned && !terminal.isCompleted) reject(error)
            Unit
        }

        override fun onComplete(error: Throwable?) = synchronized(lock) {
            if (!abandoned && !terminal.isCompleted) {
                val failure = when {
                    error is Error -> error
                    callbackFailure is Error -> callbackFailure
                    else -> callbackFailure ?: error
                }
                terminal.complete(if (failure == null) Result.success(buffer.toString()) else Result.failure(failure))
                buffer.setLength(0)
            }
            Unit
        }
    }

    /** Called under lock; retain the first failure, escalate fatal errors and discard partial output. */
    private fun reject(error: Throwable) {
        if (callbackFailure == null || error is Error && callbackFailure !is Error) {
            callbackFailure = error
            buffer.setLength(0)
            rejected.complete(error)
        }
    }

    var started: Boolean = false
        private set
    val completed: Boolean get() = terminal.isCompleted

    suspend fun await(start: (NativeReplyCallbacks) -> Unit, cancel: () -> Unit): String {
        currentCoroutineContext().ensureActive()
        started = true
        start(callbacks)
        val result: Result<String> = try {
            select<Result<String>> {
                terminal.onAwait { it }
                rejected.onAwait { failure ->
                    // Run cancellation on the owning coroutine, never inside the native callback thread.
                    stopAndAwaitTerminal(cancel)
                    Result.failure<String>(synchronized(lock) { callbackFailure ?: failure })
                }
            }
        } catch (cancelled: CancellationException) {
            stopAndAwaitTerminal(cancel)
            throw cancelled
        }
        return result.getOrThrow()
    }

    private suspend fun stopAndAwaitTerminal(cancel: () -> Unit) = withContext(NonCancellable) {
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
        // This bounds waiting after cancel returns, not an uninterruptible synchronous JNI call.
        val completion = withTimeoutOrNull(cancellationCleanupMillis) { terminal.await() }
        if (completion == null) {
            synchronized(lock) {
                abandoned = true
                buffer.setLength(0)
            }
            if (requested) onCancellationFailure()
            return@withContext
        }
        val failure = completion.exceptionOrNull()
        if (failure != null && failure !is CancellationException) throw failure
        cancellation.getOrThrow()
    }
}
