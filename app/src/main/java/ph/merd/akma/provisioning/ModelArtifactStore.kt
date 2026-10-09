package ph.merd.akma.provisioning

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** One managed private directory. No network access, model-sized buffers, or external paths. */
class ModelArtifactStore internal constructor(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val availableBytes: () -> Long,
    private val publish: (File, File) -> Unit = { temporary, destination ->
        Files.move(
            temporary.toPath(), destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
        )
    },
    private val isNoSpace: (IOException) -> Boolean = { false },
) {
    private val mutex = Mutex()

    suspend fun resolveVerified(spec: ModelArtifactSpec): ModelProvisionResult = locked(spec) {
        verify(destination(spec), spec, reused = true)
    }

    /** Source is opened only after checking whether the verified artifact already exists. */
    suspend fun importFromStream(spec: ModelArtifactSpec, source: () -> InputStream?): ModelProvisionResult = locked(spec) {
        val destination = destination(spec)
        val existing = verify(destination, spec, reused = true)
        if (existing is ModelProvisionResult.Verified) return@locked existing
        if (existing is ModelProvisionResult.Failure &&
            existing.reason !in setOf(
                ProvisionFailure.MODEL_MISSING, ProvisionFailure.SIZE_MISMATCH,
                ProvisionFailure.HASH_MISMATCH, ProvisionFailure.FORMAT_MISMATCH,
            )
        ) return@locked existing
        if (!hasSpace(spec.sizeBytes)) return@locked failure(ProvisionFailure.INSUFFICIENT_STORAGE)

        val input = source() ?: return@locked failure(ProvisionFailure.SOURCE_NOT_FOUND)
        var temporary: File? = null
        try {
            input.use {
                temporary = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
                val digest = MessageDigest.getInstance("SHA-256")
                val header = ByteArray(spec.format.signature.size)
                var headerBytes = 0
                var total = 0L
                FileOutputStream(temporary!!).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (read < 0) break
                        if (read == 0) continue
                        if (read.toLong() > spec.sizeBytes - total) {
                            return@locked failure(ProvisionFailure.SIZE_MISMATCH)
                        }
                        if (headerBytes < header.size) {
                            val count = minOf(read, header.size - headerBytes)
                            buffer.copyInto(header, headerBytes, 0, count)
                            headerBytes += count
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        total += read
                    }
                    if (total != spec.sizeBytes) return@locked failure(ProvisionFailure.SIZE_MISMATCH)
                    if (!header.contentEquals(spec.format.signature)) {
                        return@locked failure(ProvisionFailure.FORMAT_MISMATCH)
                    }
                    if (digest.digest().hex() != spec.sha256) return@locked failure(ProvisionFailure.HASH_MISMATCH)
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            // The rename is the commit point. Never publish a partial file or fall back to copying.
            publish(temporary!!, destination)
            ModelProvisionResult.Verified(VerifiedModel(destination, spec, reused = false))
        } catch (error: IOException) {
            if (error is AtomicMoveNotSupportedException) {
                failure(ProvisionFailure.ATOMIC_PUBLICATION_UNSUPPORTED)
            } else if (isNoSpace(error) || availableBytes() < STORAGE_RESERVE_BYTES) {
                failure(ProvisionFailure.INSUFFICIENT_STORAGE)
            } else {
                failure(ProvisionFailure.FILESYSTEM_ERROR)
            }
        } finally {
            // Synchronous cleanup also runs when the coroutine is cancelled.
            temporary?.delete()
        }
    }

    private suspend fun locked(
        spec: ModelArtifactSpec,
        operation: suspend () -> ModelProvisionResult,
    ): ModelProvisionResult = withContext(dispatcher) {
        if (!spec.isValid()) return@withContext failure(ProvisionFailure.INVALID_METADATA)
        if (!mutex.tryLock()) return@withContext failure(ProvisionFailure.IMPORT_BUSY)
        try {
            if (Files.isSymbolicLink(directory.toPath())) return@withContext failure(ProvisionFailure.UNSAFE_PATH)
            if (!directory.isDirectory && !directory.mkdirs()) return@withContext failure(ProvisionFailure.FILESYSTEM_ERROR)
            val lockFile = File(directory, ".import.lock")
            if (!safePath(lockFile)) return@withContext failure(ProvisionFailure.UNSAFE_PATH)
            RandomAccessFile(lockFile, "rw").use { handle ->
                val lock = try {
                    handle.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } ?: return@withContext failure(ProvisionFailure.IMPORT_BUSY)
                lock.use {
                    cleanupTemporaryFiles()
                    currentCoroutineContext().ensureActive()
                    operation()
                }
            }
        } catch (_: SecurityException) {
            failure(ProvisionFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            failure(ProvisionFailure.FILESYSTEM_ERROR)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun verify(file: File, spec: ModelArtifactSpec, reused: Boolean): ModelProvisionResult {
        if (!safePath(file)) return failure(ProvisionFailure.UNSAFE_PATH)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return failure(ProvisionFailure.MODEL_MISSING)
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return failure(ProvisionFailure.UNSAFE_PATH)
        if (file.length() != spec.sizeBytes) return failure(ProvisionFailure.SIZE_MISMATCH)
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val header = ByteArray(spec.format.signature.size)
            var offset = 0
            while (offset < header.size) {
                currentCoroutineContext().ensureActive()
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) return failure(ProvisionFailure.SIZE_MISMATCH)
                offset += read
            }
            if (!header.contentEquals(spec.format.signature)) return failure(ProvisionFailure.FORMAT_MISMATCH)
            digest.update(header)
            var total = header.size.toLong()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read.toLong() > spec.sizeBytes - total) return failure(ProvisionFailure.SIZE_MISMATCH)
                digest.update(buffer, 0, read)
                total += read
            }
            if (total != spec.sizeBytes) return failure(ProvisionFailure.SIZE_MISMATCH)
        }
        if (digest.digest().hex() != spec.sha256) return failure(ProvisionFailure.HASH_MISMATCH)
        return ModelProvisionResult.Verified(VerifiedModel(file, spec, reused))
    }

    private fun destination(spec: ModelArtifactSpec) = File(directory, spec.sha256 + spec.format.extension)

    private fun safePath(file: File): Boolean =
        !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == directory.canonicalFile

    private fun hasSpace(sizeBytes: Long): Boolean {
        val free = availableBytes()
        return free >= sizeBytes && free - sizeBytes >= STORAGE_RESERVE_BYTES
    }

    private fun cleanupTemporaryFiles() {
        directory.listFiles()?.filter {
            it.name.startsWith(TEMP_PREFIX) && it.name.endsWith(TEMP_SUFFIX)
        }?.forEach { file ->
            if (!safePath(file) || !file.isFile || !file.delete()) throw IOException("Temporary cleanup failed")
        }
    }

    companion object {
        const val STORAGE_RESERVE_BYTES: Long = 256L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val TEMP_PREFIX = ".import-"
        private const val TEMP_SUFFIX = ".partial"
        private fun failure(reason: ProvisionFailure) = ModelProvisionResult.Failure(reason)
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
