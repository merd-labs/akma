# Bundled Android model integration

The emergency release uses PR #26's bundled, uncompressed Qwen artifact and real CPU runtime. PR #22 supplies verified provisioning, not an inference engine. No second model import screen is required. The older SAF helper remains unwired; do not add it to the release journey.

## Runtime integration

The integration owner replaces PR #26's copy/rename/full-hash block with this seam. Do not run both provisioning paths. Retain one provisioner instance per engine and close the native engine before repair or replacement:

```kotlin
private val provisioner = BundledModelProvisioner(app)

// In the existing suspend initialize(), after closing the previous engine:
val model = when (val result = provisioner.ensureBundledModel(LocalModelArtifact.spec)) {
    is ModelProvisionResult.Verified -> result.model.file
    is ModelProvisionResult.Failure -> error("Model provisioning failed: ${result.reason}")
}
// Pass model.absolutePath to the actual CPU EngineConfig, then initialize it.
```

The domain LocalReplyEngine API is unchanged. Typed provisioning failure must remain an error, never Ready or a synthetic reply. On native model-loading failure, call `provisioner.invalidateVerification()` before retrying so the next attempt performs full validation. This method deletes no files. Native crash/OOM/linkage recovery remains with the runtime/domain owner.

`LocalModelArtifact.spec` records the Gemma 4 E2B pin (replacing the owner's PR #26 Qwen2.5 pin and the interim Qwen3-1.7B pin): filename `gemma-4-E2B-it.litertlm`, revision `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`, 2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, LiteRT-LM format, Apache-2.0. The pinned [source artifact](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm) publishes the same SHA. Model replacement requires owner review and fresh device proof.

## Sideloading (Gemma 4 E2B)

The file is larger than the 2 GiB single-asset limit, so it is **not bundled**. `openModelSource` uses a bundled asset when present, otherwise `<app external files>/models/<filename>`. Push it once per demo phone (the app directory exists after first launch):

```bash
adb shell mkdir -p /sdcard/Android/data/ph.merd.akma/files/models
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/ph.merd.akma/files/models/
```

On first model check the app copies it to `filesDir/models` through the same size/header/SHA-256 verification (needs about 2.6 GB plus the 256 MiB reserve free), and later runs reuse that copy. A wrong or truncated push fails as a typed provisioning error and nothing is published. The pushed file is left in place; remove it manually to reclaim space. App-scoped external storage needs no permission on API 30.


## Integrity and recovery

The bundled store uses the existing `filesDir/models/<original filename>` destination. An already verified PR #26 copy is reused without creating a hash-named duplicate. No host paths or public storage are used. No network or storage permission is added. The app's existing backup policy remains the manifest owner's responsibility.

Imports stream in 64 KiB chunks on IO. Exact byte count, eight-byte `LITERTLM` header and SHA are validated before the temporary file is synced, made read-only, and atomically renamed. Unsupported atomic replacement fails safely. Copying requires model size plus a 256 MiB free-storage reserve. Partial imports are never valid installed models; managed partial files are cleaned while holding the filesystem lock. Missing or corrupt installed copies are repaired from the bundled asset only if the replacement validates. Valid existing files survive failed replacement. The old PR #26 `model-provision.tmp` is not adopted as a verified model or deleted by this component.

The bundled provisioner caches a verified file within its own lifetime. Before reuse it checks device/inode, size, nanosecond mtime/ctime, mode and link count. Changed metadata, missing files or explicit invalidation force verification. Every new provisioner/process rehashes an existing file. When stat is unavailable, full hashing remains mandatory. Metadata must remain stable across verification. No persistent boolean or size-only receipt establishes integrity. This protects accidental corruption in app-private storage; it does not defend against privileged adversaries controlling the application UID or kernel. Do not mutate files while the native runtime holds them.

## Repeatable release verification

Use JDK 17 and the checked-in Gradle Wrapper. The `--apk` packaging checks apply only to a model bundled in the APK; Gemma 4 E2B is sideloaded, so use `--model` only for it. The integration owner owns LiteRT/Kotlin pins, Gradle, manifest and UI reconciliation. Keep weights/APKs ignored and outside commits. Download the named artifact from the pinned revision into a temporary file, verify it, then publish it to the ignored asset location. Do not build a release candidate from an incomplete download.

Ubuntu Bash:

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash scripts/provision/verify-release.sh --model "$MODEL_FILE" 2588147712 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
bash scripts/provision/verify-release.sh --apk "$APK_FILE" gemma-4-E2B-it.litertlm 2588147712 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest application-id "$APK_FILE"
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest min-sdk "$APK_FILE"
```

Windows PowerShell (use existing local installation paths):

```powershell
$env:JAVA_HOME = $Jdk17Directory
$env:ANDROID_HOME = $AndroidSdkDirectory
.\scripts\provision\verify-release.ps1 --model $ModelFile 2588147712 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\scripts\provision\verify-release.ps1 --apk $ApkFile gemma-4-E2B-it.litertlm 2588147712 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\apkanalyzer.bat" manifest application-id $ApkFile
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\apkanalyzer.bat" manifest min-sdk $ApkFile
```

The JDK verifier rejects missing/duplicate model or JNI entries, compressed model assets, bad header/size/SHA and non-ARM64 JNI ELF packaging. It records APK size/hash without revealing paths or content. Manifest checks must separately show `ph.merd.akma` and minimum SDK 30; record APK signing certificate with the existing SDK `apksigner verify --print-certs` tool. Packaging checks do not establish successful native loading. Hosted CI without the ignored model does not establish a model-enabled APK. Freeze one verified APK and use those exact bytes on Pova and Camon; cross-host debug builds need not have identical hashes.

Standalone tooling regression tests need only JDK 17:

```text
javac -d <temporary-directory> scripts/provision/VerifyReleaseArtifact.java scripts/provision/VerifyReleaseArtifactTest.java
java -cp <temporary-directory> VerifyReleaseArtifactTest
```

Never enable shell tracing, publish selectors, or log model/message contents. Native Windows execution is a separate gate from PowerShell execution on Ubuntu.

## Physical gates

Reserve an exclusive slot before ADB. Remeasure identity/API/ABI, available RAM/storage, thermal state and app PSS. Do not uninstall or clear app data to bypass signing incompatibility. Verify airplane mode enabled, Wi-Fi disabled and mobile data disabled explicitly. Use `scripts/bench/check-offline.sh SERIAL` or `scripts/bench/check-offline.ps1 -Serial SERIAL` with a private selector. These read-only probes check the selected default subscription data preference; legacy global `mobile_data` can be stale on multi-SIM Android 11. They require actual Wi-Fi service disablement and print no subscription/serial identifiers. Measure copy/hash/native initialization separately from total startup. Test actual English, Filipino and Taglish generations using manual synthetic-message paste and explicit action/tone/confirmation. Store exact outputs privately; publish only redacted measurements and fidelity findings.

Close the runtime before testing interrupted import or corruption of the owned model destination. Keep the verified bundled source intact, target only the known model file, and repair through the production path. Never corrupt another owner's file or the only trusted artifact. Restart and recovery must produce a verified model or a clear failure. Stop for severe thermal state, repeated OOM, unrecoverable native crash, or slot reclamation. Preserve sanitized native/JNI/OOM/timeout evidence. Do not estimate token throughput or claim GPU/4 GB compatibility.
