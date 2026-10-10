package ph.merd.akma.provisioning

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tiny fixtures only: these test where model bytes come from, not inference. */
class ModelSourceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "LITERTLMfixture only".toByteArray()
    private val spec = ModelArtifactSpec("side.litertlm", "rev", bytes.size.toLong(), "0".repeat(64), ModelFormat.LITERT_LM)

    private fun sideload(): File = File(temporary.root, "external").also { File(it, "models").mkdirs() }

    @Test fun bundledAssetWinsOverASideloadedCopy() {
        val dir = sideload()
        File(dir, "models/side.litertlm").writeBytes("sideloaded".toByteArray())
        val stream = openModelSource(spec, asset = { ByteArrayInputStream(bytes) }, sideloadDir = dir)!!
        assertArrayEquals(bytes, stream.use { it.readBytes() })
    }

    @Test fun missingAssetFallsBackToTheSideloadedFile() {
        val dir = sideload()
        File(dir, "models/side.litertlm").writeBytes(bytes)
        val stream = openModelSource(spec, asset = { null }, sideloadDir = dir)!!
        assertArrayEquals(bytes, stream.use { it.readBytes() })
    }

    @Test fun noAssetAndNoSideloadedFileMeansNoSource() {
        assertNull(openModelSource(spec, asset = { null }, sideloadDir = sideload()))
        assertNull(openModelSource(spec, asset = { null }, sideloadDir = null))
    }

    @Test fun aDirectoryAtTheModelPathIsNotASource() {
        val dir = sideload()
        File(dir, "models/side.litertlm").mkdirs()
        assertNull(openModelSource(spec, asset = { null }, sideloadDir = dir))
    }

    @Test fun storeImportsTheSideloadedFileThroughTheSameVerification() = kotlinx.coroutines.test.runTest {
        val dir = sideload()
        File(dir, "models/side.litertlm").writeBytes(bytes)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val real = spec.copy(sha256 = sha)
        val store = ModelArtifactStore(File(temporary.root, "files/models"), availableBytes = { Long.MAX_VALUE }, originalFilename = true)
        val ok = store.importFromStream(real) { openModelSource(real, { null }, dir) } as ModelProvisionResult.Verified
        assertArrayEquals(bytes, ok.model.file.readBytes())
        // A tampered sideload is rejected, never published.
        File(dir, "models/side.litertlm").writeBytes(bytes.copyOf().also { it[10] = 1 })
        val other = ModelArtifactStore(File(temporary.root, "files2/models"), availableBytes = { Long.MAX_VALUE }, originalFilename = true)
        assertEquals(
            ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH),
            other.importFromStream(real) { openModelSource(real, { null }, dir) },
        )
    }

    private fun verified(reused: Boolean): ModelProvisionResult =
        ModelProvisionResult.Verified(VerifiedModel(File(temporary.root, "files/models/side.litertlm"), spec, reused))

    @Test fun sideloadedSourceIsDeletedAfterANewVerifiedCopy() {
        val dir = sideload()
        val source = File(dir, "models/side.litertlm").apply { writeBytes(bytes) }
        assertTrue(consumeSideloadedSource(spec, dir, assetUsed = false, result = verified(reused = false)))
        assertFalse(source.exists())
    }

    @Test fun sideloadedSourceIsKeptWhenNothingNewWasPublishedOrTheImportFailed() {
        val dir = sideload()
        val source = File(dir, "models/side.litertlm").apply { writeBytes(bytes) }
        assertFalse(consumeSideloadedSource(spec, dir, assetUsed = false, result = verified(reused = true)))
        assertFalse(consumeSideloadedSource(spec, dir, assetUsed = false, result = ModelProvisionResult.Failure(ProvisionFailure.HASH_MISMATCH)))
        assertFalse(consumeSideloadedSource(spec, dir, assetUsed = true, result = verified(reused = false)))
        assertFalse(consumeSideloadedSource(spec, null, assetUsed = false, result = verified(reused = false)))
        assertTrue(source.exists())
    }
}
