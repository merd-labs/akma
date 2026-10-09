# Pova 2 runtime-integrity gate — 2026-10-10 PHT

This evidence separates read-only physical observations from pending integrated-runtime tests. Miguel confirmed an exclusive slot for Quaternary; no parallel device commands or inference were run. Serial and package-install path are omitted.

## Physical preflight

Observed at 2026-10-09 18:19:53 UTC (2026-10-10 02:19:53 PHT):

| Gate | Result | Observed value |
| --- | --- | --- |
| Authorized physical ADB device | PASS | `device` state |
| Device identity | PASS | TECNO LE7; Android 11; API 30 |
| CPU ABI | PASS | arm64-v8a, armeabi-v7a, armeabi |
| System memory measurement | PASS | MemTotal 5,905,908 kB; MemAvailable 1,694,116 kB; MemFree 180,032 kB |
| Free-storage measurement | PASS | /data/user/0 free 8,224,300 KiB; filesystem capacity 113,454,048 KiB |
| Strict offline inference | NOT TESTED | Airplane 0, Wi-Fi 1, mobile_data 1 during preflight; no inference attempted |

At 18:28:05 UTC, the installed baseline process reported TOTAL PSS 32,197 kB, TOTAL RSS 115,116 kB and TOTAL SWAP PSS 148 kB. Thermal status was 0; battery temperature was 29.0 °C. These are baseline snapshots, not model-inference memory or peak measurements. Android memory labels above preserve the command's reported kB units.

At 18:40–18:43 UTC, the offline radio gate became PASS: airplane mode 1, Wi-Fi setting 0 and Wi-Fi service disabled; the selected default subscription mobile-data preference was 0, with all observed data-connection states 0. Read-only Bash and PowerShell probes both returned PASS on the physical device. Android 11 retained legacy global `mobile_data=1`; the default subscription preference is the relevant setting. Subscription identifiers are not printed. Standard `svc wifi disable` and `svc data disable` returned exit code 0 after the user enabled airplane mode. This verifies radio state, not inference.

## Installed APK identity

Read from the device at 18:28:50 UTC, without installing or launching a replacement:

- Application ID: `ph.merd.akma`; versionCode 1; versionName 0.1.0; minSdk 30; targetSdk 36.
- APK size: 26,109,226 bytes.
- APK SHA-256: `9a0b5e7ee6431f05430da154086884ce4612a91bb6292d3e5e9d3e42c9342602`.
- Bundled `.litertlm` asset count: 0. This APK cannot establish the bundled-model gate.

## Model and release gates

PR #26 head `ae7f0946c6a6be26fd159650900da6769eb81008` pins the Qwen filename/revision/size/SHA documented in `docs/model-provisioning/INTEGRATION.md`. The published pinned artifact SHA matches the owner metadata. That metadata check is PASS; downloaded-byte verification is a separate gate. An existing host partial file measured 853,168,128 bytes, so it failed the expected 1,597,931,520-byte size check and was not used. A separate private download was started. At 18:43 UTC, a complete existing host download was copied into private staging and independently verified by the JDK17 streaming verifier: exact size, LITERTLM header and trusted SHA all PASS. The duplicate owned download was stopped and only its own partial file removed. No weights are committed.

| Gate | Result | Evidence or gap |
| --- | --- | --- |
| Downloaded complete model integrity | PASS | 1,597,931,520 bytes; header LITERTLM; SHA matches owner pin |
| Exact combined APK embedded model integrity | PASS | Source 53b087c; model exact size/header/SHA; uncompressed; ARM64 JNI ELF |
| Exact combined APK install/launch on API 30 | PASS | Frozen 53b087c APK independently hashed on device after installation; Activity cold launch 2,965 ms |
| Existing private model verification and native loading | PASS | Private model SHA matches the trusted pin; real CPU native initialization completed |
| First bundled import on phone | NOT TESTED | Model was already provisioned by the earlier installation |
| Cold/warm initialization | FAIL / PASS | Cold native initialization 62,915 ms exceeds 60,000 ms coordinator limit; user reached Ready after Retry; warm native initialization 1,469 ms |
| Ready after process restart | NOT TESTED | Cold launch was tested; a second full restart gate remains pending |
| Three English/Filipino/Taglish offline attempts | NOT TESTED | First English draft confirmed; Filipino and Taglish completion not yet observed |
| First English synthetic draft and fidelity | PASS | User confirms Professional / Reschedule intent preserved without invented commitments; actual 115-character reply retained privately |
| Filipino and Taglish fidelity | NOT TESTED | Pending manual attempts |
| Import interruption and corruption recovery on phone | NOT TESTED | Unit fixtures do not establish physical behavior |
| Initialization memory and thermal snapshots | PASS | Post-cold-init PSS 2,207,090 kB; RSS 2,293,032 kB; thermal status 0; these are sampled values, not peaks |
| First native analysis/draft latency | PASS | Analysis generation 59,588 ms; draft generation 55,871 ms; both completed and a draft is visible |
| Repeated-generation stability | NOT TESTED | One confirmed draft cannot establish repeated stability |
| Same frozen APK on Camon 30 | NOT TESTED | PR #26's earlier CL6/API36 generation used a different APK; mobile-data disablement was not recorded |
| Native Windows tooling execution | NOT TESTED | PowerShell-on-Ubuntu tests are separate |

## Reproduction and ownership

Use the pinned metadata and JDK 17 Bash/PowerShell commands in `docs/model-provisioning/INTEGRATION.md`. Run the artifact verifier before and after packaging. Record exact combined source commit, APK hash, signer and manifest identity, then reserve an exclusive device slot. Every ADB command must use an explicit privately stored serial selector. Verify airplane 1, Wi-Fi 0 and mobile_data 0 before testing real inference. Do not clear app data or bypass incompatible signing.

Measure total startup plus provisioning/hash/native initialization separately. Record response latency and PSS snapshots with timestamps; do not infer first-token latency or tokens/s from character counts. Capture only sanitized errors and measurements. Keep actual synthetic prompts, chosen action/tone and generated outputs in private evidence; commit fidelity findings without content. No GPU or 4 GB compatibility claim is supported.

Primary owns the combined adapter/Gradle/UI bridge; the domain reliability owner owns runtime recovery. Quaternary owns provisioning, artifact tooling and physical measurements. No owner engine/UI/coordinator/manifest/Gradle file is changed by this evidence branch. No reviewers are assigned and no merge is performed.

## Owned implementation checks

PR #22 implementation commit `e34a6d31b98e81982bc48383a0271376c76760b2` passed:

```text
JAVA_HOME=<JDK17> ANDROID_HOME=<SDK> ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL in 15m 16s
144 tests; 0 failures, 0 errors, 0 skipped
Lint: 0 errors; 16 existing warnings; 0 provisioning findings
```

The eight new bundled-store fixture tests cover original-path reuse, corruption repair, failed replacement preserving a valid model, unchanged-identity reuse, restart verification, same-size corruption with changed ctime, missing-stat fallback, explicit invalidation and mutation during verification. Fixtures never represent inference success. Thirteen JDK17 release-verifier cases passed; Bash syntax/help and PowerShell help passed on Ubuntu. Both release-verifier launchers were exercised on local fixture paths without printing them.

Both push and PR hosted Android/documentation checks PASS on `e34a6d3`: [push CI](https://github.com/merd-labs/akma/actions/runs/37974268558), [PR CI](https://github.com/merd-labs/akma/actions/runs/37974275057). The accepted combination is being built by Primary in [PR #27](https://github.com/merd-labs/akma/pull/27), which incorporates this provisioning seam. These CI runs have no committed model weights and are not physical-inference evidence.

## Frozen initial integration artifact

Primary supplied a stable debug APK built from `53b087c535f441815f600fd9ced816b4cdc54a39`. Independent JDK17 streaming verification returned:

```text
PASS: APK model size, header, SHA-256 and ARM64 ELF packaging
apk_bytes=1676683590
apk_sha256=d8f9b23a8701773a37438e2fab03d0bd5eb9ef42c74c6198370d4b121e973824
```

Independent signature verification returned exit 0 and the same signer as the observed baseline. AAPT showed application ID `ph.merd.akma`, label Akma, minSdk 30 and targetSdk 36. This proves artifact packaging/signing. Installation and initialization observations are recorded separately below; generation is not established by these checks. Primary's earlier output path was relocated into stable release staging. A separate owner build failed with host ENOSPC; that failure is not the stable artifact's result. Quaternary's own incomplete staging copy was rejected, removed, and never installed. Only owned duplicate temporary files were reclaimed.

The actual cached LiteRT Android 0.18.0 AAR measured 20,905,807 bytes with SHA-256 `706cbb8a87739b2818111bf3a19a78b8f7781586d10f80adf3415581fab7595f`. Its manifest declares min API 24; it packages arm64-v8a and x86_64 JNI. The ARM64 JNI header has ELF class 2 and machine 183. Declared library dependencies include Android, libc, libm, libdl, log, zlib and GLES/EGL system libraries. These dependency names do not prove GPU acceleration. API 30 native initialization subsequently completed on the exact frozen artifact, as recorded below.

## Pre-installation ownership conflict

At 18:59:33 UTC (02:59:33 PHT), before Quaternary issued any install or inference command, the installed APK measured 1,676,601,554 bytes and the app-private pinned model measured 1,597,931,520 bytes. The app process was running and /data free storage was 3,486,264 KiB. Both differ materially from the earlier baseline; the installed APK size also differs from the frozen candidate. Quaternary paused device commands and requested operator clarification rather than interrupting a possible active model test. At that checkpoint no installation, native initialization, generated output or throughput result was claimed. The conservative fresh-install/copy space check failed before installation; this is a preflight policy result, not an observed Android installer failure.

## Operator attribution and exact installation

Miguel clarified that the unexpected intervening installation was his and other operators were stopped. Its independently measured APK SHA was `a51890ad883dfa59d6f14a3c480a9467008d1d38d790fdd03bd5a0056b8b31b8`, matching a separate existing host artifact. This Quaternary session had not installed that APK. Shared Git author names and GitHub login do not prove which coding agent or operator installed it. The commit/command trail establishes this session's PR #22 changes; it does not establish authorship of another owner's APK.

Quaternary subsequently installed Primary's exact frozen `d8f9b23a...` artifact. Installation attempts are preserved as distinct results:

| Attempt | Result | Wall-clock observation |
| --- | --- | --- |
| Non-streamed replacement | FAIL | exit 255 after 108,529 ms; decisive error details were not retained |
| Streamed replacement | FAIL | `INSTALL_FAILED_INSUFFICIENT_STORAGE`; 124,121 ms |
| Streamed replacement after generated-cache removal | PASS | exit 0 / Success; 220,869 ms; completed 19:20:09 UTC |

Before the successful attempt, Akma alone was force-stopped and its one identified, reconstructible 1,553,141,640-byte XNNPACK weights cache was removed. The pinned model, preferences and user files were preserved. No uninstall, app-data clear, unrelated-app termination or broad cache deletion occurred. Rebuilding this cache affects cold initialization and storage requirements. Free storage sampled before the final attempt was 3,362,676 KiB; this is not a claim that cleanup increased storage by a particular amount.

The installed APK SHA was read back from the device and exactly matched `d8f9b23a8701773a37438e2fab03d0bd5eb9ef42c74c6198370d4b121e973824`. The app-private model SHA was independently verified against the owner pin. Successful installation does not establish first-time bundled import because that model existed before this session's installation.

## Genuine native initialization

Airplane mode 1, Wi-Fi setting 0 / service disabled and default-subscription mobile data 0 were reverified before launch and before the generation request. The frozen APK's cold Activity launch returned Status ok / LaunchState COLD: TotalTime 2,965 ms, WaitTime 2,969 ms; host command wall time 3,025 ms. Activity launch time is separate from model readiness. Available system memory before launch was 3,258,132 kB; thermal status 0.

The real CPU native initialization log reported 62,915 ms. At 19:24:51 UTC, process TOTAL PSS was 2,207,090 kB, TOTAL RSS 2,293,032 kB and TOTAL SWAP PSS 181 kB; thermal status 0. The coordinator in this frozen source uses a 60,000 ms operation timeout. Miguel confirmed Ready after Retry; the warm native initialization log reported 1,469 ms. At 19:30:39 UTC, a later ready-process snapshot measured PSS 465,041 kB, RSS 551,854 kB and SWAP PSS 109 kB, thermal status 0. These are different sampled lifecycle states, not a measured peak or memory leak conclusion.

The cold startup usability gate fails: native work alone exceeds the configured initialization deadline. The runtime/domain owners were notified in [PR #27](https://github.com/merd-labs/akma/pull/27#issuecomment-6087828950) to add a separate bounded initialization deadline while retaining the generation deadline. No coordinator/runtime source is changed by this evidence branch. A replacement APK incorporating that fix and PR #28 needs a new hash and its own physical gate.

Native initialization alone establishes no first-token latency, token throughput, GPU acceleration, response success, repeated-generation stability or 4 GB compatibility. The separately observed first response is recorded below. Actual synthetic prompt/output contents remain private.

## First genuine offline draft

With offline radios explicitly reverified, Miguel confirms a generated draft appeared after manual Analyze, Reschedule / Professional selection and separate Confirm. Native generation timings were 59,588 ms for analysis (210 characters logged) and 55,871 ms for drafting (115 characters logged). Each timing covers its native operation; their sum is not an end-to-end user-journey latency because user interaction occurred between operations. First-token latency and token count were not exposed.

The actual 115-character draft was captured privately from Akma-owned UI nodes, matching the native draft's character count. Only Akma nodes were retained; no clipboard contents or external application text were retained. The visible input differs from the exact requested fixture. Miguel separately confirms it was synthetic English and that the Professional / Reschedule reply preserves intent without invented availability or commitments. This is a human-observed fidelity PASS, not a guarantee for future outputs. Input, exact output and selected controls are retained only in private evidence, never CI or committed logs.

At 19:33:10 UTC, observed process PSS was 1,992,481 kB and RSS 2,090,348 kB; thermal status 0. At 19:36:49 UTC, PSS was 2,097,002 kB and RSS 2,193,284 kB; thermal status 0. These are snapshots rather than peak memory. Native analysis was close to the initial artifact's 60,000 ms deadline. No native crash or OOM was observed in this completed attempt; this does not establish repeated-run reliability.

Evidence commit `7ac4eec33236b10f1bfd43486ca8ad32ed680dc9` hosted Android and documentation checks both PASS: [push](https://github.com/merd-labs/akma/actions/runs/37980893824), [PR](https://github.com/merd-labs/akma/actions/runs/37980900012).

## Next frozen release candidate — separate gate

Primary published source `3a3071139b6474c8ba393ccdf800e98be55c2d5a`, a non-debuggable, development-signed release APK. Quaternary independently streamed and verified its embedded artifact and complete APK:

- APK bytes: 1,670,830,058; SHA-256: `3e9fe18697409195323af83c05286109b68d0bbc13622643c23321b3889672d0`.
- Embedded pinned model: exact size/header/SHA and uncompressed storage PASS; ARM64 model JNI ELF PASS.
- JDK17 apksigner: v3 verification PASS; signer SHA-256 `a7cabcfe6ca089204bed0b79e2ac0be6e2d98e3eb337ea11f9c043342cd55cff`, matching the installed candidate.
- AAPT: Akma / `ph.merd.akma` / minSdk 30 / targetSdk 36 PASS. Other transitive ABI entries do not establish model JNI support for those ABIs.
- Exact source inspection confirms separate initialization 900,000 ms and generation 240,000 ms deadlines; these are conservative limits, not measured performance.
- Exact-head hosted Android/documentation CI: PASS. Primary reports combined 280 local tests, debug/release lint and release assembly PASS; those local results are owner-reported, not rerun by Quaternary.

Physical installation, startup, offline inference, lifecycle/cancellation/retry, and repeated generation for this new release SHA remain NOT TESTED. None of the earlier debug APK's results are substituted for the release gate. Its installation will wait until the current manual test has ended.
