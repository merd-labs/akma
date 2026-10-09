# Android offline inference evidence - 2026-10-10

This is the runtime owner’s evidence from PR #26 (`ae7f094`), retained for attribution. Its APK hashes, prompt hashes and single-tap Camon measurements describe that upstream implementation, not `integration/offline-mvp`. The integration uses separate confirmation, verified provisioning and prompts branded Akma. Combined device acceptance must be recorded separately.

## Build contents and provenance

- Branch: `feat/android-litert-inference`, based on PR #17 (`9f7112d`).
- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, CPU backend. Its published Android AAR declares minSdk 24 and includes `jni/arm64-v8a/liblitertlm_jni.so`. Akma declares minSdk 30. No GPU or NPU path is enabled.
- Model: `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` from [litert-community/Qwen2.5-1.5B-Instruct](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/tree/19edb84c69a0212f29a6ef17ba0d6f278b6a1614), revision `19edb84c69a0212f29a6ef17ba0d6f278b6a1614`, Apache-2.0 license. Size: 1,597,931,520 bytes. SHA-256: `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`.
- Prompts: byte copies of `testing-llm/prompts/analyze_v2.txt` and `testing-llm/prompts/generate_v2.txt` into Android assets. SHA-256: `5cb04a03fee8b8637141fbfdc10d4e420fd099bf0897297fa01895797ea81006` and `64592219b05621659a6b4808cbc936bcec71eb54e6b7dee89b2b6817c556269a`. Android has no runtime dependency on `testing-llm`. The Qwen chat wrapper is supplied through Android `ConversationConfig.chatTemplate`.
- The ignored model file must be placed in `app/src/main/assets/` before building. Obtain the named file from the pinned model revision and verify its SHA-256 before copying. The build bundles the model; the app provisions it to internal storage automatically on first launch. There is no model picker or user import step. Neither model weights nor user messages belong in Git.

## Reproduction

From this worktree, with the verified model asset present:

```powershell
Get-FileHash app/src/main/assets/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm -Algorithm SHA256
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --console=plain
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK contains the model and is large. First launch needs additional internal storage for the provisioned copy. Production signing and distribution are separate work.

## Device run

Connected device: Tecno CL6 (Camon 30), Android API 36, ARM64. Airplane mode was enabled (`settings get global airplane_mode_on` returned `1`) and Wi-Fi was disabled. This is **not** the Tecno Pova 2 LE7/API 30 acceptance device.

On a fresh install, the app provisioned the bundled model and initialized without a user import step. Cold model initialization logged `8743 ms` (copy time is excluded). In the single-tap UI run before rebasing onto current `main`, warm initialization logged `960 ms`; analysis generation logged `30053 ms`, 245 characters; draft generation logged `22487 ms`, 57 characters. Selecting **Reschedule** once produced the offline editable draft: "Could you please suggest another time that works for you?" The draft was generated locally with airplane mode on. The model log records durations and character counts only.

After the run, `dumpsys meminfo ph.merd.akma` showed total PSS `2,003,974 kB`, total RSS `2,079,426 kB`, and total swap PSS `50,166 kB`. These are snapshots, not peak memory values. The rebased debug APK is 1,676,607,448 bytes, SHA-256 `ddbf4439792ea8dd97d98810ad387e1cc61e08d78ca94c2623f6367932e238c2`.

An earlier prompt trial produced a JSON reply that accepted the proposed time despite the selected Reschedule action. The final build uses the requested V2 prompts and rejects drafts that state availability when Reschedule was selected. This was a real model failure, not a fixture. The V2 prompt has not passed a broad quality evaluation on this Android runtime.

## Gate and limits

The actual APK installed and generated an offline reply on the connected Android 36 device. The required Pova 2/API 30 device test, including its initialization time, generation time, peak memory, and output, remains open because that device was not connected. APK minSdk and AAR/native library inspection establish package compatibility, not Pova 2 performance or reliability. The coordinator's timeout handles cancellable calls and discards late output; synchronous native inference may only return control after the native call finishes.
