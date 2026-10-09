package ph.merd.akma.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeviceDiagnostics(context: Context) {
    private val application = context.applicationContext

    suspend fun snapshot(): DeviceRuntimeSnapshot = withContext(Dispatchers.IO) {
        val memory = readOrNull {
            val manager = application.getSystemService(ActivityManager::class.java)
                ?: return@readOrNull null
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }
        val pss = readOrNull { Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss }
        DeviceRuntimeSnapshot(
            androidApi = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            process64Bit = Process.is64Bit(),
            totalMemoryBytes = memory?.totalMem,
            availableMemoryBytes = memory?.availMem,
            lowMemory = memory?.lowMemory,
            appPssKiB = pss,
            availableStorageBytes = readOrNull { StatFs(application.noBackupFilesDir.path).availableBytes },
            thermalStatus = readOrNull {
                application.getSystemService(PowerManager::class.java)?.currentThermalStatus
            },
        )
    }

    private inline fun <T> readOrNull(read: () -> T?): T? = try {
        read()
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
