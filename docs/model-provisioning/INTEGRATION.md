# Android model provisioning

This component imports a user-selected local artifact into application-private storage and returns a verified native-runtime file. It does not implement inference, modify the domain API, launch UI, install a runtime dependency, or download a model. Production still uses the unavailable engine until Elijah integrates his genuine implementation.

## Owner metadata and integration seam

Elijah supplies a reviewed `ModelArtifactSpec(filename, revision, sizeBytes, sha256, format)`. Size is the exact number of bytes; SHA-256 is lowercase hexadecimal. Revision identifies the reviewed source artifact. The current format is `ModelFormat.LITERT_LM`. No model candidate's hash or revision is guessed or embedded in this component.

`AndroidModelProvisioner(context)` creates a managed store under `context.noBackupFilesDir/models`. Models are named `<trusted-sha256>.litertlm`, not by an untrusted document display name. No-backup storage survives process restart and normal application updates, but not uninstall or app-data clearing. This adds no Internet or broad storage permission.

The Activity owner launches `AndroidModelProvisioner.selectionIntent()` with their existing activity-result mechanism. After a user selects a local file, call `importFromUri(uri, ownerSpec)` from a lifecycle coroutine. The intent requests local-only documents, but provider compliance is not a network-isolation guarantee; use an already local file. The temporary URI grant is used immediately. Nothing retains URI strings or persistable grants.

In Elijah's `initialize()`, call `resolveVerified(ownerSpec)` before creating the real runtime:

```kotlin
when (val result = provisioner.resolveVerified(ownerSpec)) {
    is ModelProvisionResult.Verified -> {
        val privateModelPath = result.model.file.absolutePath
        // Pass privateModelPath to the owner's actual pinned CPU runtime.
        // Only the real native initialization result establishes readiness.
    }
    is ModelProvisionResult.Failure -> {
        // Map result.reason to the existing recoverable model/error state.
        // Do not mark ready or substitute a generated-looking response.
    }
}
```

An already provisioned managed file uses the same hash-derived name and must pass `resolveVerified`; markers, filenames and old receipts never establish verification. Do not move, modify or delete a returned file while the native engine is using it. This component does not prune previously verified artifacts. UI/runtime owners manage selection and model lifetime; importing a different hash does not delete the previous artifact.

## Guarantees and limits

- Streaming uses a 64 KiB buffer on `Dispatchers.IO`, with exact byte counts and SHA-256. The first eight bytes must be `LITERTLM`, as documented in the inspected upstream [format recognizer](https://github.com/google-ai-edge/LiteRT-LM/blob/8111f12b64e0c62c37a53e02f59f196ef2aa4603/runtime/util/file_format_util.cc). This header identifies the container, not its native-runtime/version compatibility.
- The store requires artifact size plus a 256 MiB free-space reserve before copying. This is a storage policy, not a RAM requirement or promised model capacity.
- The temporary file lives beside its destination, is synced, and is published only after complete validation with `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. Unsupported atomic publication fails without copying into the final name. A lost rename after a power failure may leave the artifact missing; resolution always rehashes rather than trusting an installed flag.
- Same-store coroutine exclusion and a filesystem lock serialize operations across instances/processes. Concurrent calls return `IMPORT_BUSY`; they do not start another copy. A verified duplicate returns `reused=true` without opening its source, including when free space would not support a second copy.
- Cancellation propagates as coroutine cancellation, closes the source and deletes the operation's temporary file. Local reads check cancellation between chunks. A misbehaving provider's blocking read cannot be forcibly interrupted by those checks; no resumable/background import is promised. Atomic rename is the commit point; cancellation arriving after that point may leave a complete verified file installed.
- Restart cleanup removes only managed `.import-*.partial` files while holding the lock. Partial files never resolve as models. A failed import preserves previous verified artifacts.
- Failures are typed: invalid metadata, missing source/model, revoked permission, unsafe path, insufficient storage, size/hash/format mismatch, busy import, unsupported atomic publication or filesystem error. No raw provider exception, path, URI, identifier or model content is returned in error text. `VerifiedModel.toString()` omits its private path.

## Compatibility diagnostics

`AndroidDeviceDiagnostics(context).snapshot()` reads API, ABI, process bitness, system memory, app PSS, app-private filesystem capacity and thermal status. Failed optional readings remain null. It reports no serial, package-install path, clipboard, message or model output.

`RuntimeCompatibility.assess(snapshot, ownerRuntimeDescriptor, verifiedResult)` checks the exact pinned Android dependency/version, minimum API, packaged ARM64 libraries, declared CPU backend and supported format. Missing runtime metadata or unverified model cannot pass. `prechecksPass` means static prechecks only; never label it successful initialization or inference. Native library packaging must be checked in the actual AAR/APK because libraries can be compressed in the APK instead of extracted into `nativeLibraryDir`.

`classifyNativeFailure(observedError)` maps JNI/linkage, OOM, file-access and other initialization errors to safe categories. Elijah calls it only for real observed runtime errors. Coroutine cancellation must be rethrown before error mapping. A native process crash or Android low-memory kill cannot be caught by this Kotlin classifier; record it separately without publishing unredacted logs.

## Ubuntu and Windows reproduction

Use JDK 17 and the checked-in Gradle Wrapper. Do not change shared build pins or add a Python/backend dependency.

Ubuntu Bash:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME="$ANDROID_HOME" \
  ./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
bash scripts/provision/verify-artifact.sh "$LOCAL_MODEL_FILE" "$OWNER_SIZE_BYTES" "$OWNER_SHA256"
```

Windows PowerShell (set JDK/SDK paths to existing installations):

```powershell
$env:JAVA_HOME = $Jdk17Directory
$env:ANDROID_HOME = $AndroidSdkDirectory
.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\scripts\provision\verify-artifact.ps1 -Artifact $LocalModelFile -ExpectedBytes $OwnerSizeBytes -ExpectedSha256 $OwnerSha256
```

Both verifier scripts perform local streaming hash checks and emit status only. They do not transfer a model, invoke ADB, change radio settings, or establish native compatibility. Never enable tracing around private paths or a device selector. Windows-native execution must be reported separately from PowerShell execution on Ubuntu.

Reserve an exclusive Pova 2 slot before installing the integrated model-enabled APK. Remeasure RAM/storage, verify APK identity, choose the local artifact through SAF, verify import, restart, interrupt a separate import, and test missing/corrupt files using disposable fixtures. Never deliberately corrupt the only real artifact or clear another owner's app data. Keep fixtures clearly labeled as provisioning tests, never AI models. Once the genuine runtime exists, prioritize one real offline reply, then repeated English/Filipino/Taglish generations with synthetic messages. Save actual outputs privately; commit only reviewed hashes, measurements and fidelity findings. Stop for severe thermal status, repeated OOM or slot reclamation.
