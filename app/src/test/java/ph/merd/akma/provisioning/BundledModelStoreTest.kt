package ph.merd.akma.provisioning

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tiny container fixtures exercise provisioning only; none are inference models. */
class BundledModelStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory get() = File(temporary.root, "models")
    private val bytes = "LITERTLMfixture only".toByteArray()
    private fun spec(data: ByteArray = bytes) = ModelArtifactSpec(
        "bundle.litertlm", "fixture-revision", data.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) },
        ModelFormat.LITERT_LM,
    )
    private fun store(identity: ((File) -> ModelFileIdentity?)? = null) = ModelArtifactStore(
        directory, availableBytes = { Long.MAX_VALUE }, originalFilename = true, identity = identity,
    )
    private fun key(changedNanos: Long = 1) = ModelFileIdentity(1, 2, bytes.size.toLong(), 3, 4, 5, changedNanos, 256, 1)

    @Test fun reusesExistingRuntimeFilenameWithoutCreatingHashNamedDuplicate() = runTest {
        directory.mkdirs()
        val existing = File(directory, spec().filename).apply { writeBytes(bytes) }
        val result = store().importFromStream(spec()) { error("No second asset copy") } as ModelProvisionResult.Verified
        assertTrue(result.model.reused)
        assertEquals(existing, result.model.file)
        assertEquals(1, directory.listFiles()!!.count { it.extension == "litertlm" })
    }

    @Test fun corruptedExistingCopyRepairsOnlyWithVerifiedBundle() = runTest {
        directory.mkdirs()
        val target = File(directory, spec().filename).apply { writeBytes(bytes.copyOf().also { it[9] = 0 }) }
        val store = store()
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH),
            store.importFromStream(spec()) { ByteArrayInputStream(target.readBytes()) })
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH), store.resolveVerified(spec()))
        val repaired = store.importFromStream(spec()) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        assertFalse(repaired.model.reused)
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun failedReplacementPreservesPreviousValidModel() = runTest {
        val store = store()
        val first = store.importFromStream(spec()) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        val next = bytes.copyOf().also { it[9] = 1 }
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH),
            store.importFromStream(spec(next)) { ByteArrayInputStream(bytes) })
        assertArrayEquals(bytes, first.model.file.readBytes())
        assertTrue(store.resolveVerified(spec()) is ModelProvisionResult.Verified)
    }

    @Test fun unchangedIdentityReusesValidationButNewProcessFullyVerifies() = runTest {
        var observations = 0
        val identity: (File) -> ModelFileIdentity? = { observations++; key() }
        val first = store(identity)
        first.importFromStream(spec()) { ByteArrayInputStream(bytes) }
        observations = 0
        assertTrue(first.resolveVerified(spec()) is ModelProvisionResult.Verified)
        assertEquals("Cached verification performs one metadata check", 1, observations)
        observations = 0
        assertTrue(store(identity).resolveVerified(spec()) is ModelProvisionResult.Verified)
        assertEquals("New store verifies stable metadata before and after full read", 2, observations)
    }

    @Test fun statusChangeDetectsSameSizeCorruptionEvenWithUnchangedModificationTime() = runTest {
        var changed = 1L
        val store = store { key(changed) }
        val result = store.importFromStream(spec()) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        result.model.file.writeBytes(bytes.copyOf().also { it[9] = 0 })
        changed++ // Simulate ctime nanoseconds; size, inode and mtime are unchanged.
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH), store.resolveVerified(spec()))
    }

    @Test fun unavailableMetadataNeverEnablesVerificationCache() = runTest {
        val store = store { null }
        val result = store.importFromStream(spec()) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        result.model.file.writeBytes(bytes.copyOf().also { it[9] = 0 })
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH), store.resolveVerified(spec()))
    }

    @Test fun invalidationForcesFullReadBeforeNativeRetry() = runTest {
        val store = store { key() }
        val result = store.importFromStream(spec()) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        // Deliberately fixed test metadata exercises explicit invalidation, not a real stat claim.
        result.model.file.writeBytes(bytes.copyOf().also { it[9] = 0 })
        store.invalidateVerification()
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH), store.resolveVerified(spec()))
    }

    @Test fun fileChangeDuringVerificationCannotCreateVerifiedState() = runTest {
        directory.mkdirs()
        File(directory, spec().filename).writeBytes(bytes)
        var observations = 0L
        assertEquals(ModelProvisionResult.Failure(ProvisionFailure.FILESYSTEM_ERROR),
            store { key(++observations) }.resolveVerified(spec()))
    }
}
