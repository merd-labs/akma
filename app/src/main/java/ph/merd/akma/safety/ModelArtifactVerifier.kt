package ph.merd.akma.safety

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/** Pinned identity of one model file. All three fields must come from reviewed source, never from the network. */
data class ModelArtifactSpec(val fileName: String, val sha256Hex: String, val sizeBytes: Long)

enum class ArtifactRejection {
    /** Spec fields are malformed (bad file name, non-lowercase-hex or missing SHA-256, non-positive/oversized size). */
    SPEC_INVALID,
    NOT_FOUND,
    SYMLINK,

    /** Canonical location is not directly inside the expected app-private directory. */
    OUTSIDE_APP_STORAGE,
    WRONG_SIZE,
    HASH_MISMATCH,
    CANCELLED,
    IO_ERROR,
}

sealed interface ArtifactCheck {
    /** [file] is the canonical path that was hashed. Load exactly this file, not a re-derived path. */
    data class Verified(val file: File) : ArtifactCheck
    data class Rejected(val reason: ArtifactRejection) : ArtifactCheck
}

/**
 * Integrity gate for a local model file before any native runtime opens it.
 *
 * It enforces: pinned SHA-256 and size (a missing hash is a rejection, never "skip"), a plain file name that
 * resolves directly inside the given app-private directory (no traversal, no symlink), streaming hash with
 * cancellation, constant-time digest comparison. It never logs and never puts paths or exception text in results.
 *
 * It does not prove the model is safe or licensed; it proves the bytes match what the team reviewed. Pure JVM
 * (java.io, java.nio.file, java.security): works on Android API 30.
 */
object ModelArtifactVerifier {
    const val DEFAULT_MAX_BYTES = 4L * 1024 * 1024 * 1024
    private const val CHUNK = 1024 * 1024
    private val fileNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val hexPattern = Regex("[0-9a-f]{64}")

    /**
     * @param directory app-private directory (e.g. `File(context.filesDir, "models")`), never shared storage.
     * @param isCancelled polled between 1 MiB chunks; return true to stop hashing promptly.
     */
    fun verify(
        directory: File,
        spec: ModelArtifactSpec,
        isCancelled: () -> Boolean = { false },
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): ArtifactCheck {
        if (!fileNamePattern.matches(spec.fileName) || spec.fileName.contains("..") ||
            !hexPattern.matches(spec.sha256Hex) || spec.sizeBytes <= 0 || spec.sizeBytes > maxBytes
        ) {
            return ArtifactCheck.Rejected(ArtifactRejection.SPEC_INVALID)
        }
        return try {
            val candidate = File(directory, spec.fileName)
            val path = candidate.toPath()
            if (Files.isSymbolicLink(path)) return ArtifactCheck.Rejected(ArtifactRejection.SYMLINK)
            if (!candidate.isFile) return ArtifactCheck.Rejected(ArtifactRejection.NOT_FOUND)
            val canonicalDirectory = directory.canonicalFile
            val canonical = candidate.canonicalFile
            if (canonical.parentFile != canonicalDirectory) return ArtifactCheck.Rejected(ArtifactRejection.OUTSIDE_APP_STORAGE)
            if (canonical.length() != spec.sizeBytes) return ArtifactCheck.Rejected(ArtifactRejection.WRONG_SIZE)

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            canonical.inputStream().use { input ->
                val buffer = ByteArray(CHUNK)
                while (true) {
                    if (isCancelled()) return ArtifactCheck.Rejected(ArtifactRejection.CANCELLED)
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > spec.sizeBytes) return ArtifactCheck.Rejected(ArtifactRejection.WRONG_SIZE) // grew while hashing
                    digest.update(buffer, 0, read)
                }
            }
            if (total != spec.sizeBytes) return ArtifactCheck.Rejected(ArtifactRejection.WRONG_SIZE)
            val expected = hexToBytes(spec.sha256Hex)
            if (MessageDigest.isEqual(digest.digest(), expected)) {
                ArtifactCheck.Verified(canonical)
            } else {
                ArtifactCheck.Rejected(ArtifactRejection.HASH_MISMATCH)
            }
        } catch (_: IOException) {
            ArtifactCheck.Rejected(ArtifactRejection.IO_ERROR)
        } catch (_: SecurityException) {
            ArtifactCheck.Rejected(ArtifactRejection.IO_ERROR)
        }
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
