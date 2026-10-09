package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException

/** Optional native lifecycle capability. The coordinator calls this only while holding its engine mutex. */
interface RuntimeRecovery {
    /** True when native ownership is uncertain and process restart is required. */
    val requiresRestart: Boolean get() = false

    /** Detach unusable handles before closing them. A failed cleanup must prevent subsequent initialization. */
    suspend fun invalidateRuntime(): Result<Unit>
}

class RuntimeRestartRequiredException : IllegalStateException("Local runtime cleanup failed.")

/** A close failure makes native ownership uncertain. Never reuse or repeatedly close that handle. */
internal class NativeHandleSlot<T : AutoCloseable> {
    @Volatile var current: T? = null
        private set
    @Volatile var quarantined: Boolean = false
        private set

    fun install(handle: T) {
        if (quarantined) throw RuntimeRestartRequiredException()
        check(current == null) { "Native handle is already installed." }
        current = handle
    }

    fun quarantine() {
        current = null
        quarantined = true
    }

    fun invalidate(): Result<Unit> {
        if (quarantined) return Result.failure(RuntimeRestartRequiredException())
        val detached = current
        current = null
        var closed = false
        return try {
            detached?.close()
            closed = true
            Result.success(Unit)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: LinkageError) {
            Result.failure(failure)
        } catch (failure: OutOfMemoryError) {
            Result.failure(failure)
        } catch (failure: Exception) {
            Result.failure(failure)
        } finally {
            // Unexpected fatal Errors propagate, and their partially closed handles are never reused.
            if (!closed) quarantined = true
        }
    }
}
