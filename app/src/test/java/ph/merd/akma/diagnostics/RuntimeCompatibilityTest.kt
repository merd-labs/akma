package ph.merd.akma.diagnostics

import java.io.File
import ph.merd.akma.provisioning.ModelArtifactSpec
import ph.merd.akma.provisioning.ModelFormat
import ph.merd.akma.provisioning.ModelProvisionResult
import ph.merd.akma.provisioning.VerifiedModel
import org.junit.Assert.*
import org.junit.Test

class RuntimeCompatibilityTest {
    private val device = DeviceRuntimeSnapshot(30, listOf("arm64-v8a"), true,
        6_000_000_000L, 2_000_000_000L, false, 80_000, 8_000_000_000L, 0)
    private val runtime = RuntimeDescriptor("test-runtime", "fixture", 30, setOf("arm64-v8a"), true, setOf(ModelFormat.LITERT_LM))
    // Typed verification fixture tests prechecks only; no runtime or model execution is mocked.
    private val model = ModelProvisionResult.Verified(VerifiedModel(File("private-fixture"),
        ModelArtifactSpec("fixture.litertlm", "fixture", 8, "0".repeat(64), ModelFormat.LITERT_LM), true))

    @Test fun absentRuntimeAndArtifactCannotPass() {
        val report = RuntimeCompatibility.assess(device, null, null)
        assertFalse(report.prechecksPass)
        assertTrue(report.findings.containsAll(setOf(CompatibilityFinding.RUNTIME_UNKNOWN, CompatibilityFinding.MODEL_NOT_VERIFIED)))
    }

    @Test fun compatibilityPrecheckDoesNotClaimNativeExecution() {
        assertTrue(RuntimeCompatibility.assess(device, runtime, model).prechecksPass)
    }

    @Test fun rejectsUnsupportedApiAbiProcessAndBackend() {
        val report = RuntimeCompatibility.assess(device.copy(process64Bit = false),
            runtime.copy(minimumApi = 31, packagedAbis = setOf("x86_64"), cpuBackendAvailable = false), model)
        assertTrue(report.findings.containsAll(setOf(CompatibilityFinding.ANDROID_API_UNSUPPORTED,
            CompatibilityFinding.ARM64_LIBRARY_MISSING, CompatibilityFinding.PROCESS_NOT_ARM64,
            CompatibilityFinding.CPU_BACKEND_UNAVAILABLE)))
    }

    @Test fun reportsMemoryPressureMissingStorageFormatAndThermalStop() {
        val report = RuntimeCompatibility.assess(device.copy(lowMemory = true, availableStorageBytes = null, thermalStatus = 3),
            runtime.copy(supportedFormats = emptySet()), model)
        assertTrue(report.findings.containsAll(setOf(CompatibilityFinding.MEMORY_PRESSURE,
            CompatibilityFinding.STORAGE_UNKNOWN, CompatibilityFinding.MODEL_FORMAT_UNSUPPORTED,
            CompatibilityFinding.SEVERE_THERMAL_STATUS)))
    }

    @Test fun invalidRuntimeDescriptorCannotPass() {
        assertFalse(RuntimeCompatibility.assess(device, runtime.copy(dependency = "", minimumApi = 0), model).prechecksPass)
    }

    @Test fun unknownMemoryAndThermalReadingsCannotPassPrechecks() {
        val report = RuntimeCompatibility.assess(device.copy(availableMemoryBytes = null, thermalStatus = null), runtime, model)
        assertFalse(report.prechecksPass)
        assertTrue(report.findings.containsAll(setOf(CompatibilityFinding.MEMORY_UNKNOWN, CompatibilityFinding.THERMAL_UNKNOWN)))
    }

    @Test fun wrappedLinkageFailureRetainsItsCategory() {
        assertEquals(NativeFailure.JNI_OR_LIBRARY_LINKAGE,
            RuntimeCompatibility.classifyNativeFailure(IllegalStateException("redacted", UnsatisfiedLinkError("redacted"))))
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun cancellationIsNeverClassifiedAsNativeFailure() {
        RuntimeCompatibility.classifyNativeFailure(java.util.concurrent.CancellationException("cancelled"))
    }

    @Test fun nativeFailureCategoriesNeverExposeErrorDetails() {
        for ((error, expected) in listOf(
            UnsatisfiedLinkError("private library path") to NativeFailure.JNI_OR_LIBRARY_LINKAGE,
            NoClassDefFoundError("private runtime") to NativeFailure.JNI_OR_LIBRARY_LINKAGE,
            OutOfMemoryError("private allocation") to NativeFailure.OUT_OF_MEMORY,
            java.io.FileNotFoundException("private model") to NativeFailure.MODEL_FILE_ACCESS,
            SecurityException("private model") to NativeFailure.MODEL_FILE_ACCESS,
            IllegalStateException("private request") to NativeFailure.INITIALIZATION_FAILED,
        )) {
            val result = RuntimeCompatibility.classifyNativeFailure(error)
            assertEquals(expected, result)
            assertFalse(result.toString().contains("private"))
        }
    }
}
