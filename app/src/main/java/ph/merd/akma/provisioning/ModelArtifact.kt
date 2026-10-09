package ph.merd.akma.provisioning

import java.io.File

/** Metadata must come from the model owner, never from a document provider. */
data class ModelArtifactSpec(
    val filename: String,
    val revision: String,
    val sizeBytes: Long,
    val sha256: String,
    val format: ModelFormat,
) {
    internal fun isValid(): Boolean =
        filename.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) &&
            filename.endsWith(format.extension) &&
            revision.isNotBlank() && revision.length <= 256 &&
            revision.none { it.isISOControl() } &&
            sizeBytes >= format.signature.size &&
            sha256.matches(Regex("[a-f0-9]{64}"))
}

enum class ModelFormat(val extension: String, internal val signature: ByteArray) {
    // Container signature documented by LiteRT-LM runtime/util/file_format_util.cc.
    // This is not a claim that a particular runtime can execute the container.
    LITERT_LM(".litertlm", "LITERTLM".toByteArray(Charsets.US_ASCII)),
}

enum class ProvisionFailure {
    INVALID_METADATA,
    SOURCE_NOT_FOUND,
    PERMISSION_DENIED,
    MODEL_MISSING,
    UNSAFE_PATH,
    INSUFFICIENT_STORAGE,
    SIZE_MISMATCH,
    HASH_MISMATCH,
    FORMAT_MISMATCH,
    IMPORT_BUSY,
    ATOMIC_PUBLICATION_UNSUPPORTED,
    FILESYSTEM_ERROR,
}

/** The path is for the native runtime only; do not log it or include it in diagnostics. */
class VerifiedModel internal constructor(
    val file: File,
    val spec: ModelArtifactSpec,
    val reused: Boolean,
) {
    override fun toString(): String = "VerifiedModel(format=${spec.format}, sizeBytes=${spec.sizeBytes}, reused=$reused)"
}

sealed interface ModelProvisionResult {
    data class Verified(val model: VerifiedModel) : ModelProvisionResult
    data class Failure(val reason: ProvisionFailure) : ModelProvisionResult
}
