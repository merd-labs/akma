package ph.merd.akma.provisioning

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelArtifactStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun directory() = File(temporary.root, "models")
    private fun fixture(payload: String = "fixture, not inference") =
        "LITERTLM$payload".toByteArray(Charsets.UTF_8)
    private fun spec(bytes: ByteArray) = ModelArtifactSpec(
        "fixture.litertlm", "unit-test-revision", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) },
        ModelFormat.LITERT_LM,
    )
    private fun store(
        free: Long = Long.MAX_VALUE,
        publish: ((File, File) -> Unit)? = null,
    ): ModelArtifactStore = if (publish == null) {
        ModelArtifactStore(directory(), availableBytes = { free })
    } else {
        ModelArtifactStore(directory(), availableBytes = { free }, publish = publish)
    }
    private fun assertFailure(reason: ProvisionFailure, result: ModelProvisionResult) {
        assertEquals(ModelProvisionResult.Failure(reason), result)
    }
    private fun assertNoPartial() {
        assertTrue(directory().listFiles().orEmpty().none { it.name.endsWith(".partial") })
    }

    @Test fun streamsInBoundedBuffersAndPublishesExactBytes() = runTest {
        val bytes = fixture("x".repeat(200_000))
        var largestRead = 0
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                largestRead = maxOf(largestRead, length)
                return super.read(buffer, offset, minOf(length, 1023))
            }
            override fun close() { closed = true; super.close() }
        }
        val result = store().importFromStream(spec(bytes)) { input } as ModelProvisionResult.Verified
        assertArrayEquals(bytes, result.model.file.readBytes())
        assertFalse(result.model.reused)
        assertTrue(largestRead <= 64 * 1024)
        assertTrue(closed)
        assertNoPartial()
    }

    @Test fun duplicateImportDoesNotOpenSourceOrRequireAnotherStorageReservation() = runTest {
        val bytes = fixture()
        val spec = spec(bytes)
        val store = store()
        val first = store.importFromStream(spec) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        val second = store(free = 0).importFromStream(spec) {
            fail("Already verified artifact must not open a source")
            null
        } as ModelProvisionResult.Verified
        assertTrue(second.model.reused)
        assertEquals(first.model.file, second.model.file)
        assertEquals(1, directory().listFiles().orEmpty().count { it.name.endsWith(".litertlm") })
    }

    @Test fun restartRehashesAndDetectsSameSizeCorruption() = runTest {
        val bytes = fixture()
        val spec = spec(bytes)
        val first = store().importFromStream(spec) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        assertTrue(store().resolveVerified(spec) is ModelProvisionResult.Verified)
        val corrupt = bytes.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        first.model.file.writeBytes(corrupt)
        assertFailure(ProvisionFailure.HASH_MISMATCH, store().resolveVerified(spec))
    }

    @Test fun deliberatelyCorruptImportNeverPublishes() = runTest {
        val bytes = fixture()
        val corrupt = bytes.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        val store = store()
        assertFailure(ProvisionFailure.HASH_MISMATCH, store.importFromStream(spec(bytes)) { ByteArrayInputStream(corrupt) })
        assertFailure(ProvisionFailure.MODEL_MISSING, store.resolveVerified(spec(bytes)))
        assertNoPartial()
    }

    @Test fun exactHashStillRequiresExpectedContainerSignature() = runTest {
        val bytes = "WRONGFMTnot a model".toByteArray()
        assertFailure(ProvisionFailure.FORMAT_MISMATCH, store().importFromStream(spec(bytes)) { ByteArrayInputStream(bytes) })
        assertNoPartial()
    }

    @Test fun rejectsTruncatedAndOversizedSources() = runTest {
        val bytes = fixture()
        val store = store()
        for (source in listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0))) {
            assertFailure(ProvisionFailure.SIZE_MISMATCH, store.importFromStream(spec(bytes)) { ByteArrayInputStream(source) })
            assertFailure(ProvisionFailure.MODEL_MISSING, store.resolveVerified(spec(bytes)))
            assertNoPartial()
        }
    }

    @Test fun missingSourceAndModelHaveDistinctFailures() = runTest {
        val spec = spec(fixture())
        assertFailure(ProvisionFailure.MODEL_MISSING, store().resolveVerified(spec))
        assertFailure(ProvisionFailure.SOURCE_NOT_FOUND, store().importFromStream(spec) { null })
        assertNoPartial()
    }

    @Test fun insufficientStorageRejectsBeforeOpeningSourceAndChecksReserveBoundary() = runTest {
        val bytes = fixture()
        val spec = spec(bytes)
        assertFailure(ProvisionFailure.INSUFFICIENT_STORAGE,
            store(free = spec.sizeBytes + ModelArtifactStore.STORAGE_RESERVE_BYTES - 1).importFromStream(spec) {
                fail("Must check capacity before opening source"); null
            })
        assertTrue(store(free = spec.sizeBytes + ModelArtifactStore.STORAGE_RESERVE_BYTES)
            .importFromStream(spec) { ByteArrayInputStream(bytes) } is ModelProvisionResult.Verified)
    }

    @Test fun invalidMetadataCannotCreateDirectoryOrOpenSource() = runTest {
        val valid = spec(fixture())
        for (invalid in listOf(
            valid.copy(filename = "../fixture.litertlm"), valid.copy(filename = "fixture.gguf"),
            valid.copy(revision = ""), valid.copy(revision = "revision\n"),
            valid.copy(sizeBytes = -1), valid.copy(sha256 = "unknown"),
        )) {
            assertFailure(ProvisionFailure.INVALID_METADATA, store().importFromStream(invalid) {
                fail("Invalid metadata must not open source"); null
            })
        }
        assertFalse(directory().exists())
    }

    @Test fun interruptedCopyClosesSourceAndLeavesNoInstalledState() = runTest {
        val bytes = fixture("x".repeat(100_000))
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            var calls = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (calls++ > 0) throw IOException("private source data must not escape")
                return super.read(buffer, offset, length)
            }
            override fun close() { closed = true }
        }
        val result = store().importFromStream(spec(bytes)) { input }
        assertFailure(ProvisionFailure.FILESYSTEM_ERROR, result)
        assertFalse(result.toString().contains("private source"))
        assertTrue(closed)
        assertFailure(ProvisionFailure.MODEL_MISSING, store().resolveVerified(spec(bytes)))
        assertNoPartial()
    }

    @Test fun cancellationPropagatesAndCleansTemporaryFile() = runTest {
        val bytes = fixture("x".repeat(100_000))
        var closed = false
        val store = store()
        val job = async(start = CoroutineStart.LAZY) {
            val jobContext = coroutineContext
            store.importFromStream(spec(bytes)) {
                object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        jobContext[kotlinx.coroutines.Job]!!.cancel()
                        return super.read(buffer, offset, length)
                    }
                    override fun close() { closed = true }
                }
            }
        }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(closed)
        assertFailure(ProvisionFailure.MODEL_MISSING, store.resolveVerified(spec(bytes)))
        assertNoPartial()
    }

    @Test fun atomicPublicationFailurePreservesOtherVerifiedArtifact() = runTest {
        val oldBytes = fixture("existing")
        val newBytes = fixture("replacement")
        val oldSpec = spec(oldBytes)
        val previous = store().importFromStream(oldSpec) { ByteArrayInputStream(oldBytes) } as ModelProvisionResult.Verified
        val failing = store(publish = { _, _ -> throw AtomicMoveNotSupportedException("private", "private", "test") })
        assertFailure(ProvisionFailure.ATOMIC_PUBLICATION_UNSUPPORTED,
            failing.importFromStream(spec(newBytes)) { ByteArrayInputStream(newBytes) })
        assertArrayEquals(oldBytes, previous.model.file.readBytes())
        assertTrue(store().resolveVerified(oldSpec) is ModelProvisionResult.Verified)
        assertFailure(ProvisionFailure.MODEL_MISSING, store().resolveVerified(spec(newBytes)))
        assertNoPartial()
    }

    @Test fun corruptExistingArtifactCanBeReplacedOnlyByVerifiedImport() = runTest {
        val bytes = fixture()
        val spec = spec(bytes)
        val result = store().importFromStream(spec) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        result.model.file.writeBytes(bytes.copyOf().also { it[8] = 0 })
        val restored = store().importFromStream(spec) { ByteArrayInputStream(bytes) } as ModelProvisionResult.Verified
        assertFalse(restored.model.reused)
        assertArrayEquals(bytes, restored.model.file.readBytes())
    }

    @Test fun abandonedPartialIsRemovedOnRestartButNeverResolvedAsModel() = runTest {
        directory().mkdirs()
        val partial = File(directory(), ".import-abandoned.partial").apply { writeBytes(fixture()) }
        assertFailure(ProvisionFailure.MODEL_MISSING, store().resolveVerified(spec(fixture())))
        assertFalse(partial.exists())
    }

    @Test fun modelAndRootSymlinksAreRejectedWithoutFollowingThem() = runTest {
        val bytes = fixture()
        val spec = spec(bytes)
        directory().mkdirs()
        val external = temporary.newFile().apply { writeBytes(bytes) }
        Files.createSymbolicLink(File(directory(), spec.sha256 + ".litertlm").toPath(), external.toPath())
        assertFailure(ProvisionFailure.UNSAFE_PATH, store().resolveVerified(spec))
        assertFailure(ProvisionFailure.UNSAFE_PATH, store().importFromStream(spec) { ByteArrayInputStream(bytes) })
        assertArrayEquals(bytes, external.readBytes())
        val rootLink = File(temporary.root, "linked-models")
        Files.createSymbolicLink(rootLink.toPath(), directory().toPath())
        assertFailure(ProvisionFailure.UNSAFE_PATH,
            ModelArtifactStore(rootLink, availableBytes = { Long.MAX_VALUE }).resolveVerified(spec))
    }

    @Test fun permissionFailureIsRedacted() = runTest {
        val result = store().importFromStream(spec(fixture())) { throw SecurityException("secret URI") }
        assertFailure(ProvisionFailure.PERMISSION_DENIED, result)
        assertFalse(result.toString().contains("secret"))
    }

    @Test fun midWriteNoSpaceUsesExplicitFailureCategory() = runTest {
        val bytes = fixture()
        val store = ModelArtifactStore(directory(), availableBytes = { Long.MAX_VALUE },
            publish = { _, _ -> throw IOException("ENOSPC") }, isNoSpace = { true })
        assertFailure(ProvisionFailure.INSUFFICIENT_STORAGE, store.importFromStream(spec(bytes)) { ByteArrayInputStream(bytes) })
        assertNoPartial()
    }

    @Test fun partialCopySpaceConsumptionDoesNotMislabelSourceFailure() = runTest {
        val bytes = fixture("x".repeat(100_000))
        var free = Long.MAX_VALUE
        val store = ModelArtifactStore(directory(), availableBytes = { free })
        val input = object : ByteArrayInputStream(bytes) {
            var calls = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (calls++ > 0) {
                    free = ModelArtifactStore.STORAGE_RESERVE_BYTES + 1
                    throw IOException("Source failed after partial storage allocation")
                }
                return super.read(buffer, offset, length)
            }
        }
        assertFailure(ProvisionFailure.FILESYSTEM_ERROR, store.importFromStream(spec(bytes)) { input })
        assertNoPartial()
    }

    @Test fun concurrentStoresUseFilesystemLockAndBusyCallDoesNotOpenSource() = runTest {
        val bytes = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = store()
        val job = async {
            first.importFromStream(spec(bytes)) {
                object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return super.read(buffer, offset, length)
                    }
                }
            }
        }
        // Dispatch the test coroutine before waiting for its IO worker.
        kotlinx.coroutines.yield()
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            for (other in listOf(first, store())) {
                assertFailure(ProvisionFailure.IMPORT_BUSY, other.importFromStream(spec(bytes)) {
                    fail("Busy import must not open source"); null
                })
            }
        } finally {
            release.countDown()
        }
        assertTrue(job.await() is ModelProvisionResult.Verified)
        assertNoPartial()
    }

    @Test fun verifiedResultDoesNotPrintPrivatePath() = runTest {
        val bytes = fixture()
        val result = store().importFromStream(spec(bytes)) { ByteArrayInputStream(bytes) }
        assertFalse(result.toString().contains(temporary.root.path))
    }
}
