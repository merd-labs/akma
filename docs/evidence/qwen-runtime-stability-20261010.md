# Integrated Qwen runtime check — 2026-10-10

This records the `fix/qwen-runtime-stability` build on a connected TECNO CL6 (Camon 30), Android API 36, ARM64. It does not satisfy the Pova 2/API 30 gate. All entered messages were synthetic; no model or message content was committed.

## Build and package

- Model asset: 1,597,931,520 bytes; SHA-256 `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9` (Get-FileHash matched).
- Final command: `.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests ph.merd.akma.domain.ReplyValidationTest --tests ph.merd.akma.domain.DraftConfirmationTest --tests ph.merd.akma.domain.NativeErrorResilienceTest --tests ph.merd.akma.safety.ModelOutputSafetyTest :app:assembleDebug --console=plain` — BUILD SUCCESSFUL in 41s.
- Full suite before the final callback edit: `:app:compileDebugKotlin :app:testDebugUnitTest` compiled; 228 tests ran, one failed. `ModelArtifactStoreTest.modelAndRootSymlinksAreRejectedWithoutFollowingThem` could not create a Windows symlink: "A required privilege is not held by the client." The failure precedes the production code assertion. The final focused tests passed.
- Final APK: 1,676,787,462 bytes; SHA-256 `fdcd5d75bfb73b94301e86c6133a586a93dc1f9003dc12c048a05ba9822cdcf9`. `verify-release.ps1 --apk` passed model size, header, hash and ARM64 ELF packaging. `aapt dump badging` reported `ph.merd.akma`, minSdk 30, targetSdk 36. `apksigner verify --print-certs` passed with an Android Debug certificate.

## Offline device run

The first three observations below used APK SHA-256 `877b72763ca76008fd09c82a587739b294bb70859e11d2244f2cdc8914d25782`. A final rebuild changed native failure classification. `adb install -r` returned Success for the final APK (SHA-256 `fdcd5d75bfb73b94301e86c6133a586a93dc1f9003dc12c048a05ba9822cdcf9`), and the successful Ask for details path was repeated on those exact bytes. Before generation, `airplane_mode_on=1`, `wifi_on=0`, and `mobile_data=0`. Akma reached Ready and the visible Activity controls were used: enter synthetic message, Analyze, select action, review the action and tone, then tap the separate Confirm and Generate Draft control. No automatic send occurred. All generated drafts remained editable.

| Synthetic scenario | Observed result |
| --- | --- |
| Short Friday interview question, Reschedule, Professional | Analysis succeeded (31,710 ms; first token 14,631 ms). Draft generated (22,294 ms; first token 19,702 ms) but was a generic refusal, so it failed action fidelity. |
| Clearer job interview invitation | Analysis produced unusable output twice (25,733 ms and 30,898 ms). Akma showed a recoverable processing error; no draft was claimed. |
| Short Friday interview question, Ask for details, Professional | Analysis succeeded (33,421 ms; first token 15,389 ms). After a separate confirmation, the model generated an editable, relevant question asking for interview details (21,270 ms; first token 18,608 ms). This is one genuine offline end-to-end generation, with limited quality evidence. |
| Same Ask for details path, final APK | Analysis succeeded (36,800 ms; first token 17,884 ms). A separate confirmation produced the same relevant, editable question (21,473 ms; first token 18,898 ms) with airplane mode on, Wi-Fi off and mobile data off. |

Warm native initialization logged 1,105 ms. A post-run `dumpsys meminfo ph.merd.akma` snapshot showed total PSS 2,094,759 kB, RSS 2,146,830 kB and swap PSS 73,832 kB; these are not peaks. Provisioning/cold initialization time was not measured for this final APK. After the run, radio settings were restored and verified to their original `airplane_mode_on=0`, `wifi_on=1`, `mobile_data=1` values.

The Pova 2/API 30 device, repeatability, multilingual quality, and Reschedule fidelity remain unverified or failed. The package's minSdk 30 and ARM64 contents do not establish Pova 2 runtime compatibility.
