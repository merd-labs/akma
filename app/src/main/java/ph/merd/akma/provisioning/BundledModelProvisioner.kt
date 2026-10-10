package ph.merd.akma.provisioning

import android.content.Context
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Opens the model bytes for [ModelArtifactStore.importFromStream]: a bundled asset first, then a sideloaded
 * copy at `<sideloadDir>/models/<filename>` (app-scoped external storage, written with `adb push`).
 * The filename is validated by the spec, and only a regular file inside that directory is opened.
 * Returns null when neither exists, which the store reports as SOURCE_NOT_FOUND.
 */
internal fun openModelSource(spec: ModelArtifactSpec, asset: () -> InputStream?, sideloadDir: File?): InputStream? {
    asset()?.let { return it }
    val file = sideloadDir?.let { File(File(it, "models"), spec.filename) } ?: return null
    if (!file.isFile) return null
    return try {
        FileInputStream(file)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/** Automatic local provisioning (asset or adb sideload); no picker, downloads, or broad storage permissions. */
class BundledModelProvisioner(context: Context) {
    private val application = context.applicationContext
    private val store = ModelArtifactStore(
        directory = File(application.filesDir, "models"),
        availableBytes = { StatFs(application.filesDir.path).availableBytes },
        originalFilename = true,
        identity = { file ->
            try {
                val stat = Os.lstat(file.absolutePath)
                ModelFileIdentity(
                    stat.st_dev, stat.st_ino, stat.st_size,
                    stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec,
                    stat.st_ctim.tv_sec, stat.st_ctim.tv_nsec,
                    stat.st_mode, stat.st_nlink,
                )
            } catch (_: ErrnoException) {
                null // Full verification remains mandatory when stat is unavailable.
            }
        },
        publish = { temporary, destination ->
            // Prevent accidental writes while the runtime holds the verified file.
            if (!temporary.setReadOnly()) throw java.io.IOException("Model protection failed")
            Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        },
        isNoSpace = { error ->
            generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<ErrnoException>().any { it.errno == OsConstants.ENOSPC }
        },
    )

    /** Keep this instance for the engine lifetime. Close native engine before repair/replacement. */
    suspend fun ensureBundledModel(spec: ModelArtifactSpec): ModelProvisionResult =
        store.importFromStream(spec) {
            openModelSource(
                spec,
                asset = {
                    try {
                        application.assets.open(spec.filename)
                    } catch (_: FileNotFoundException) {
                        null
                    }
                },
                sideloadDir = application.getExternalFilesDir(null),
            )
        }

    suspend fun invalidateVerification() = store.invalidateVerification()
}
