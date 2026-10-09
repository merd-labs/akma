package ph.merd.akma.provisioning

import android.content.Context
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Automatic local asset provisioning; no picker, downloads, or broad storage permissions. */
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
            try {
                application.assets.open(spec.filename)
            } catch (_: FileNotFoundException) {
                null
            }
        }

    suspend fun invalidateVerification() = store.invalidateVerification()
}
