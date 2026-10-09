# Provisioning validation — 2026-10-10 PHT

Production provisioning and safe diagnostics are implemented; no inference adapter or UI integration is included. Baseline is approved Android main `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`, containing the team-merged CI, safety, stability and overlay changes. The owning branch is `feat/android-model-provisioning`. See [integration instructions](INTEGRATION.md) for the exact Kotlin interface and both host platforms.

## Local checks

The executed command used the existing private Android SDK directory; the command below substitutes its `ANDROID_HOME` variable without changing targets or flags.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME="$ANDROID_HOME" \
  ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Observed exit **0**, `BUILD SUCCESSFUL in 7m 28s`, **136 tests, 0 failures/errors/skips**. Of these, **20 provisioning** and **9 compatibility** methods are new. Lint: **0 errors, 16 warnings**, **0 findings in owned production packages**. Existing SDK-tool/Gradle deprecation warnings remain. This replaces earlier verification on older common baselines; none of the team branches was merged by this operator.

Debug APK: application ID **`ph.merd.akma`**, minSdk **30**, targetSdk **36**, **26,208,038 bytes**, SHA-256 **`454a231417e7d8f5d18b0a61594522ed9ef8278c346beeae5b473efc8a6e6e1f`**. The actual packaged manifest has **no Internet or broad storage permission**. This APK contains the provisioning component but retains the unavailable inference engine. It was **not installed** during this session.

Bash `-n`, PowerShell parser validation and both help commands exited **0**. Six fixture scenarios passed on each verifier: exact bytes/hash/header, wrong hash, wrong size, missing file, invalid metadata and wrong container header. An additional Bash backslash/newline filename case passed without path disclosure. PowerShell execution was on Ubuntu; Windows-native execution is **NOT TESTED**. Fixtures are tiny provisioning test inputs, not executable AI models or generated responses. No Python/backend dependency was added.

## Actual physical baseline

Miguel confirmed a fresh exclusive Pova 2 slot with Elijah and Secondary stopped. Selected-device operations ran **2026-10-09 16:24:32–16:25:09 UTC / 2026-10-10 00:24:32–00:25:09 PHT**. [Release was announced](https://github.com/merd-labs/akma/issues/5#issuecomment-6084916581). No further ADB operations are authorized by this released slot.

- TECNO **LE7**, Android **11 / API 30**, **arm64-v8a**, authorized physical transport.
- At **16:24:33 UTC**, MemTotal **5,905,908 KiB**, MemAvailable **1,893,968 KiB**, free queried filesystem **8,282,260 KiB**, filesystem size **113,454,048 KiB**. These are real baseline readings, not inference memory.
- Existing installed `ph.merd.akma` base APK was privately pulled and hashed: **26,156,046 bytes**, SHA-256 **`f358bcd75a7d3319d8a52fca0a2df75abce2a1d9942cfab41e4c1e87ba7cb2af`**. It matches the previously measured unavailable-engine APK; it is different from this branch's newly built artifact.
- At **16:25:09 UTC**, `am start -W` returned **exit 0**, **Status ok**, **LaunchState WARM**, **TotalTime 300 ms**, **WaitTime 306 ms**. No cold/model/first-token timing was measured.
- Radio flags were **airplane mode 0, Wi-Fi 1, mobile data 1**. No radio changes, APK replacement, model copy or app-data clear occurred.

## Gate results and remaining integration

| Gate | Result | Evidence or limitation |
|---|---|---|
| Production Kotlin compilation, debug build and lint | PASS | Command and results above |
| Streaming into a private filesystem with bounded buffers | PASS | Actual JVM filesystem tests; Android SAF path not exercised |
| Corrupt/truncated/oversized/wrong-format rejection | PASS | Fixture size/hash/signature tests |
| Partial import, cancellation and restart cleanup | PASS | Filesystem/coroutine tests; no partial final state |
| Missing model/source, insufficient storage, atomic publication failure | PASS | Typed failure tests, including mid-copy errors |
| Duplicate import and concurrent exclusion | PASS | Verified reuse without source open; mutex/filesystem-lock tests |
| API 30 packaging; no Internet/broad-storage permission | PASS | Actual APK manifest inspection |
| Fresh Pova 2 identity, available memory/storage, existing APK launch | PASS | Actual readings above; no provisioning inference claim |
| Airplane-mode disabled-radio prerequisites | FAIL | Flags 0/1/1 at observation |
| New APK install and SAF-selected real artifact import/hash | NOT TESTED | No owner metadata/UI hook; existing phone installation retained |
| Android process restart/interruption and corrupt/missing artifact UX | NOT TESTED | Requires owner-integrated picker build and disposable fixtures |
| Pinned Android runtime minimum API/ARM64/CPU/native model loading | NOT TESTED | Runtime dependency/build not supplied in inspected branches |
| Genuine offline reply, repeated memory/latency/crash/thermal behavior | NOT TESTED | No model-enabled APK; no generation attempted |
| GPU execution | NOT TESTED | No backend execution evidence |
| Windows-native wrapper/tests/scripts | NOT TESTED | Ubuntu/JDK 17 and PowerShell-on-Ubuntu only |

Runtime branches/PRs were monitored directly. Latest inspected Elijah head is **PR #20**, `feat/qwen-prompt-protocol`, `89b2492ef449f9c49c43c370bd5a2fbbdade70b6`: protocol/parser plus Gson, with no Android inference engine or LiteRT-LM native dependency. PR #16 remains desktop research. Exact trusted model SHA-256/revision and pinned Android runtime are not invented.

Elijah receives the verified app-private path from `resolveVerified(ownerSpec)` inside `initialize()`. Activity owner connects the supplied SAF intent/import result to existing model/error states. Domain signatures, Activity/overlay, manifest and Gradle are unchanged by this branch. Native readiness must come from actual initialization. Hosted CI is inspected on the pushed head and recorded in the PR; local success does not establish hosted success. Team integration can take this independent provisioning seam, then owner-coordinated picker/runtime hooks, followed by a fresh exclusive offline device session on the exact integrated APK. Only fast-forward updates from approved main established this branch's baseline. No reviewer request, merge commit or merge into main is performed by this operator.
