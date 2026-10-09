package ph.merd.akma.provisioning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.system.ErrnoException
import android.system.OsConstants
import java.io.File
import java.io.FileNotFoundException

/** UI launches [selectionIntent], then calls [importFromUri] from its lifecycle coroutine. */
class AndroidModelProvisioner(context: Context) {
    private val application = context.applicationContext
    private val store = ModelArtifactStore(
        directory = File(application.noBackupFilesDir, "models"),
        availableBytes = { StatFs(application.noBackupFilesDir.path).availableBytes },
        isNoSpace = { error ->
            generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<ErrnoException>().any { it.errno == OsConstants.ENOSPC }
        },
    )

    suspend fun resolveVerified(spec: ModelArtifactSpec): ModelProvisionResult = store.resolveVerified(spec)

    suspend fun importFromUri(uri: Uri, spec: ModelArtifactSpec): ModelProvisionResult {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            return ModelProvisionResult.Failure(ProvisionFailure.UNSAFE_PATH)
        }
        return store.importFromStream(spec) {
            try {
                application.contentResolver.openInputStream(uri)
            } catch (_: FileNotFoundException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    companion object {
        /** EXTRA_LOCAL_ONLY is a provider preference; the caller must select an available local file. */
        fun selectionIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Providers classify unknown model extensions differently; trusted bytes decide validity.
            type = "*/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
