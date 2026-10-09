package ph.merd.akma.diagnostics

import ph.merd.akma.provisioning.ModelFormat
import ph.merd.akma.provisioning.ModelProvisionResult

/** Populate from the exact pinned Android runtime package, not its desktop counterpart. */
data class RuntimeDescriptor(
    val dependency: String,
    val version: String,
    val minimumApi: Int,
    val packagedAbis: Set<String>,
    val cpuBackendAvailable: Boolean,
    val supportedFormats: Set<ModelFormat>,
)

/** Selected safe values only. Unknown readings stay null; never substitute a plausible value. */
data class DeviceRuntimeSnapshot(
    val androidApi: Int,
    val supportedAbis: List<String>,
    val process64Bit: Boolean,
    val totalMemoryBytes: Long?,
    val availableMemoryBytes: Long?,
    val lowMemory: Boolean?,
    val appPssKiB: Int?,
    val availableStorageBytes: Long?,
    val thermalStatus: Int?,
)

enum class CompatibilityFinding {
    RUNTIME_UNKNOWN,
    RUNTIME_METADATA_INVALID,
    ANDROID_API_UNSUPPORTED,
    PROCESS_NOT_ARM64,
    ARM64_LIBRARY_MISSING,
    CPU_BACKEND_UNAVAILABLE,
    MODEL_NOT_VERIFIED,
    MODEL_FORMAT_UNSUPPORTED,
    MEMORY_PRESSURE,
    MEMORY_UNKNOWN,
    STORAGE_UNKNOWN,
    THERMAL_UNKNOWN,
    SEVERE_THERMAL_STATUS,
}

data class CompatibilityReport(val findings: Set<CompatibilityFinding>) {
    /** Passing prechecks does not establish native loading or genuine inference. */
    val prechecksPass: Boolean get() = findings.isEmpty()
}

enum class NativeFailure {
    JNI_OR_LIBRARY_LINKAGE,
    OUT_OF_MEMORY,
    MODEL_FILE_ACCESS,
    INITIALIZATION_FAILED,
}

object RuntimeCompatibility {
    fun assess(
        device: DeviceRuntimeSnapshot,
        runtime: RuntimeDescriptor?,
        model: ModelProvisionResult?,
    ): CompatibilityReport {
        val findings = mutableSetOf<CompatibilityFinding>()
        if (runtime == null) {
            findings += CompatibilityFinding.RUNTIME_UNKNOWN
        } else {
            if (runtime.dependency.isBlank() || runtime.version.isBlank() || runtime.minimumApi < 1) {
                findings += CompatibilityFinding.RUNTIME_METADATA_INVALID
            }
            if (device.androidApi < runtime.minimumApi) findings += CompatibilityFinding.ANDROID_API_UNSUPPORTED
            if ("arm64-v8a" !in runtime.packagedAbis) findings += CompatibilityFinding.ARM64_LIBRARY_MISSING
            if (!runtime.cpuBackendAvailable) findings += CompatibilityFinding.CPU_BACKEND_UNAVAILABLE
            if (model is ModelProvisionResult.Verified && model.model.spec.format !in runtime.supportedFormats) {
                findings += CompatibilityFinding.MODEL_FORMAT_UNSUPPORTED
            }
        }
        if (!device.process64Bit || "arm64-v8a" !in device.supportedAbis) {
            findings += CompatibilityFinding.PROCESS_NOT_ARM64
        }
        if (model !is ModelProvisionResult.Verified) findings += CompatibilityFinding.MODEL_NOT_VERIFIED
        if (device.lowMemory == true) findings += CompatibilityFinding.MEMORY_PRESSURE
        if (device.availableMemoryBytes == null || device.lowMemory == null) findings += CompatibilityFinding.MEMORY_UNKNOWN
        if (device.availableStorageBytes == null) findings += CompatibilityFinding.STORAGE_UNKNOWN
        if (device.thermalStatus == null) findings += CompatibilityFinding.THERMAL_UNKNOWN
        else if (device.thermalStatus >= 3) findings += CompatibilityFinding.SEVERE_THERMAL_STATUS
        return CompatibilityReport(findings)
    }

    /** Classify an observed error from the owner engine; never expose its text or stack trace. */
    fun classifyNativeFailure(error: Throwable): NativeFailure {
        // Bound traversal so malformed cyclic causes cannot hang diagnostics.
        val causes = generateSequence(error) { it.cause }.take(16).toList()
        causes.filterIsInstance<java.util.concurrent.CancellationException>().firstOrNull()?.let { throw it }
        return when {
            causes.any { it is OutOfMemoryError } -> NativeFailure.OUT_OF_MEMORY
            causes.any { it is LinkageError } -> NativeFailure.JNI_OR_LIBRARY_LINKAGE
            causes.any { it is java.io.IOException || it is SecurityException } -> NativeFailure.MODEL_FILE_ACCESS
            else -> NativeFailure.INITIALIZATION_FAILED
        }
    }
}
