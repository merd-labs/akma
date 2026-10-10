package ph.merd.akma.domain

import ph.merd.akma.provisioning.ProvisionFailure

/** Typed owner-defined categories produce constant notices without exposing paths or native error text. */
internal class LocalModelProvisioningException(val reason: ProvisionFailure) :
    IllegalStateException("Local model provisioning failed.") {
    val userNotice: String get() = when (reason) {
        ProvisionFailure.INSUFFICIENT_STORAGE -> "Not enough free storage for the local model. Free space and retry."
        ProvisionFailure.MODEL_MISSING, ProvisionFailure.SOURCE_NOT_FOUND ->
            "Model file not found. Copy gemma-4-E2B-it.litertlm into this app's models folder (see the README), then tap Try again."
        ProvisionFailure.SIZE_MISMATCH, ProvisionFailure.HASH_MISMATCH, ProvisionFailure.FORMAT_MISMATCH ->
            "The model file failed verification. Copy it again from the verified download, then tap Try again."
        ProvisionFailure.INVALID_METADATA -> "Model metadata is invalid. Install the verified Akma APK."
        ProvisionFailure.PERMISSION_DENIED -> "Akma cannot access private model storage. Restart Akma and retry."
        ProvisionFailure.UNSAFE_PATH, ProvisionFailure.ATOMIC_PUBLICATION_UNSUPPORTED ->
            "Akma cannot safely publish the local model. Restart Akma or reinstall the verified APK."
        ProvisionFailure.IMPORT_BUSY -> "The local model is still being prepared. Wait and retry."
        ProvisionFailure.FILESYSTEM_ERROR -> "Local model preparation failed. Check free storage and retry."
    }
}
