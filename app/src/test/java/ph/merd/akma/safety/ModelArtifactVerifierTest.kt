package ph.merd.akma.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

/** Synthetic bytes only; no real model is involved. */
class ModelArtifactVerifierTest {
    @get:Rule val temp = TemporaryFolder()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun modelDir(): File = temp.newFolder("models")

    private fun write(dir: File, name: String, bytes: ByteArray): ModelArtifactSpec {
        File(dir, name).writeBytes(bytes)
        return ModelArtifactSpec(name, sha(bytes), bytes.size.toLong())
    }

    private fun reason(check: ArtifactCheck) = (check as ArtifactCheck.Rejected).reason

    @Test fun matchingFilePassesAndReturnsTheCanonicalFile() {
        val dir = modelDir()
        val spec = write(dir, "model.litertlm", Random(1).nextBytes(3 * 1024 * 1024 + 17)) // spans several chunks
        val check = ModelArtifactVerifier.verify(dir, spec)
        assertTrue(check.toString(), check is ArtifactCheck.Verified)
        assertEquals(File(dir, "model.litertlm").canonicalFile, (check as ArtifactCheck.Verified).file)
    }

    @Test fun flippedByteIsAHashMismatch() {
        val dir = modelDir()
        val bytes = Random(2).nextBytes(10_000)
        val spec = write(dir, "m.bin", bytes)
        bytes[5000] = (bytes[5000] + 1).toByte()
        File(dir, "m.bin").writeBytes(bytes)
        assertEquals(ArtifactRejection.HASH_MISMATCH, reason(ModelArtifactVerifier.verify(dir, spec)))
    }

    @Test fun truncatedOrExtendedFileIsWrongSize() {
        val dir = modelDir()
        val spec = write(dir, "m.bin", Random(3).nextBytes(4096))
        File(dir, "m.bin").appendBytes(byteArrayOf(1))
        assertEquals(ArtifactRejection.WRONG_SIZE, reason(ModelArtifactVerifier.verify(dir, spec)))
        File(dir, "m.bin").writeBytes(ByteArray(10))
        assertEquals(ArtifactRejection.WRONG_SIZE, reason(ModelArtifactVerifier.verify(dir, spec)))
    }

    @Test fun missingFileIsNotFound() {
        val dir = modelDir()
        val spec = ModelArtifactSpec("absent.bin", "a".repeat(64), 10)
        assertEquals(ArtifactRejection.NOT_FOUND, reason(ModelArtifactVerifier.verify(dir, spec)))
    }

    @Test fun invalidSpecsAreRejectedNeverSkipped() {
        val dir = modelDir()
        val good = "a".repeat(64)
        listOf(
            ModelArtifactSpec("m.bin", "", 10),                       // missing hash must not mean "skip verification"
            ModelArtifactSpec("m.bin", good.uppercase(), 10),         // strict lowercase hex
            ModelArtifactSpec("m.bin", "a".repeat(63), 10),
            ModelArtifactSpec("m.bin", "g".repeat(64), 10),
            ModelArtifactSpec("m.bin", good, 0),
            ModelArtifactSpec("m.bin", good, -5),
            ModelArtifactSpec("m.bin", good, ModelArtifactVerifier.DEFAULT_MAX_BYTES + 1),
            ModelArtifactSpec("", good, 10),
            ModelArtifactSpec("../m.bin", good, 10),
            ModelArtifactSpec("a/b.bin", good, 10),
            ModelArtifactSpec("a\\b.bin", good, 10),
            ModelArtifactSpec("..", good, 10),
            ModelArtifactSpec("m..bin", good, 10),
            ModelArtifactSpec(".hidden", good, 10),
            ModelArtifactSpec("m.bin\u0000.txt", good, 10),
            ModelArtifactSpec("/etc/passwd", good, 10),
        ).forEach { spec ->
            assertEquals(spec.toString(), ArtifactRejection.SPEC_INVALID, reason(ModelArtifactVerifier.verify(dir, spec)))
        }
    }

    @Test fun symlinkToOutsideFileIsRejectedEvenWhenBytesMatch() {
        val dir = modelDir()
        val outside = temp.newFile("outside.bin").apply { writeBytes(Random(4).nextBytes(2048)) }
        val link = File(dir, "linked.bin").toPath()
        val created = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
        assumeTrue("symbolic links unavailable on this host (needs privilege on some Windows setups)", created)
        val spec = ModelArtifactSpec("linked.bin", sha(outside.readBytes()), outside.length())
        assertEquals(ArtifactRejection.SYMLINK, reason(ModelArtifactVerifier.verify(dir, spec)))
    }

    @Test fun symlinkedDirectoryComponentCannotEscapeTheModelDirectory() {
        val real = temp.newFolder("elsewhere")
        val spec = write(real, "m.bin", Random(5).nextBytes(512))
        val parent = temp.newFolder("parent")
        val aliasDir = File(parent, "models")
        val created = runCatching { Files.createSymbolicLink(aliasDir.toPath(), real.toPath()) }.isSuccess
        assumeTrue("symbolic links unavailable on this host", created)
        // The directory itself is a link, but the file is directly inside its canonical target: allowed and returned canonically.
        val check = ModelArtifactVerifier.verify(aliasDir, spec)
        assertTrue(check.toString(), check is ArtifactCheck.Verified)
        assertEquals(File(real, "m.bin").canonicalFile, (check as ArtifactCheck.Verified).file)
    }

    @Test fun cancellationStopsHashingPromptly() {
        val dir = modelDir()
        val spec = write(dir, "big.bin", Random(6).nextBytes(8 * 1024 * 1024))
        var polls = 0
        val check = ModelArtifactVerifier.verify(dir, spec, isCancelled = { ++polls > 2 })
        assertEquals(ArtifactRejection.CANCELLED, reason(check))
        assertTrue("hashing should stop after a few chunks, polled $polls times", polls in 3..4)
    }

    @Test fun resultsNeverContainPathsOrMessages() {
        val dir = modelDir()
        val spec = ModelArtifactSpec("absent.bin", "b".repeat(64), 1)
        val text = ModelArtifactVerifier.verify(dir, spec).toString()
        assertFalse(text.contains(dir.path))
        assertFalse(text.contains("absent"))
    }
}
