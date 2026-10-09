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

Unless explicitly stated, physical results below refer to initial source `53b087c` / APK `d8f9b23a...`. The newer release has separate gates at the end of this document.

PR #26 head `ae7f0946c6a6be26fd159650900da6769eb81008` pins the Qwen filename/revision/size/SHA documented in `docs/model-provisioning/INTEGRATION.md`. The published pinned artifact SHA matches the owner metadata. That metadata check is PASS; downloaded-byte verification is a separate gate. An existing host partial file measured 853,168,128 bytes, so it failed the expected 1,597,931,520-byte size check and was not used. A separate private download was started. At 18:43 UTC, a complete existing host download was copied into private staging and independently verified by the JDK17 streaming verifier: exact size, LITERTLM header and trusted SHA all PASS. The duplicate owned download was stopped and only its own partial file removed. No weights are committed.

| Gate | Result | Evidence or gap |
| --- | --- | --- |
| Downloaded complete model integrity | PASS | 1,597,931,520 bytes; header LITERTLM; SHA matches owner pin |
| Exact combined APK embedded model integrity | PASS | Source 53b087c; model exact size/header/SHA; uncompressed; ARM64 JNI ELF |
| Exact combined APK install/launch on API 30 | PASS | Frozen 53b087c APK independently hashed on device after installation; Activity cold launch 2,965 ms |
| Existing private model verification and native loading | PASS | Private model SHA matches the trusted pin; real CPU native initialization completed |
| Interrupted first bundled import | PASS | 16,515,072-byte partial observed; destination absent after process stop; original preserved |
| Restarted import completion on initial APK | FAIL | Orphan removed but no verified publication during bounded observation; original restored |
| Cold/warm initialization | FAIL / PASS | Cold native initialization 62,915 ms exceeds 60,000 ms coordinator limit; user reached Ready after Retry; warm native initialization 1,469 ms |
| Ready after process restart | NOT TESTED | Cold launch was tested; a second full restart gate remains pending |
| Three consecutive offline attempts | PASS | English, Filipino and Taglish each completed native analysis and drafting; semantic results differ |
| First English synthetic draft and fidelity | PASS | User confirms Professional / Reschedule intent preserved without invented commitments; actual 115-character reply retained privately |
| Filipino fidelity | FAIL | Exact synthetic fixture; actual parsed reply is an unsupported English refusal; user report exactly matches private UI evidence |
| Taglish fidelity | FAIL | Synthetic input matches requested words after punctuation normalization; user reports wrong language or intent |
| Import interruption and cleanup on phone | PASS | Partial never published; restart removed orphan |
| Physical same-size corruption repair | NOT TESTED | Requires latest-source debug diagnostic artifact; JVM cases already pass |
| Initialization memory and thermal snapshots | PASS | Post-cold-init PSS 2,207,090 kB; RSS 2,293,032 kB; thermal status 0; these are sampled values, not peaks |
| First native analysis/draft latency | PASS | Analysis generation 59,588 ms; draft generation 55,871 ms; both completed and a draft is visible |
| Three-attempt native completion | PASS | Six native generation operations completed; no observed native crash/OOM; no long-run stability guarantee |
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
- Exact-head hosted Android/documentation CI: PASS. Quaternary independently reran combined tests, debug assembly and debug/release lint against this source: 280 tests passed, as recorded below. Release assembly remains owner-reported; Quaternary independently verified the resulting release artifact.

Physical installation PASS: exit 0, 105,221 ms, completed 20:32:04 UTC. The installed APK was independently hashed and exactly matches the release SHA above. Native initialization PASS: 64,651 ms. Miguel confirms Akma reaches Ready without the earlier Retry step. The Activity launch observer timed out after 90 s; Activity TotalTime/WaitTime are NOT TESTED, rather than an inferred application failure.

At 20:37:02 UTC, the running release process reported PSS 2,174,437 kB and RSS 2,269,032 kB; thermal status 0. At 20:40:19 UTC, PSS was 2,008,771 kB, RSS 2,112,900 kB and SWAP PSS 167 kB; thermal status 0. Airplane mode 1, Wi-Fi setting 0 / service disabled and default-subscription mobile data 0 were explicitly reverified before the release generation request. The first exact-release offline inference attempt completed, as recorded below. Two consecutive release attempts completed; the remaining matrix and restart/cancellation gates were stopped at Miguel's instruction when the official demo device changed. Earlier debug APK results are not substituted for these gates.

## Second attempt — Filipino semantic failure

The requested synthetic Filipino fixture was observed byte-exact in Akma's input field. With Reschedule / Professional requested, native analysis completed in 58,157 ms (379 raw characters logged), then native drafting in 49,839 ms (225 raw characters logged). Akma reached Edit your draft with a parsed 40-character reply. Raw native character counts are not necessarily the displayed reply length because the adapter can extract a JSON reply field.

Miguel reported failure, then supplied the observed reply privately in the QA conversation. It exactly matched the separately retained Akma UI field. The reply is a generic English refusal of a benign interview rescheduling request, so language and selected-intent fidelity are FAIL. No reply text is committed. This is a genuine native inference result with an unacceptable product response; it is not an observed native/JNI/OOM failure or a visible runtime error banner.

The engine/prompt owner received precise reproduction and private evidence location in [PR #26](https://github.com/merd-labs/akma/pull/26#issuecomment-6088127232) and [PR #27](https://github.com/merd-labs/akma/pull/27#issuecomment-6088118698). Quaternary changes no inference or prompt code. The new release's timeout/recovery changes alone do not establish that this semantic failure is fixed.

At 19:50:08 UTC: available RAM 2,680,204 kB; /data/user/0 free 3,550,044 KiB; battery temperature 27.6 °C. At 19:48:56 UTC: process PSS 1,457,986 kB; RSS 1,454,740 kB; SWAP PSS 81,408 kB; thermal status 0; process running. Swap and resident totals are different measures and are not interchangeable. Neither these snapshots nor a successful native operation establish peak memory, leak absence or repeated semantic reliability.

Evidence-only head `f61291fded6077717b97e5e53f4cdb0179134a56` hosted Android/documentation checks PASS: [push](https://github.com/merd-labs/akma/actions/runs/37981847233), [PR](https://github.com/merd-labs/akma/actions/runs/37981854217).

## Third attempt — Taglish fidelity failure

The third input matched the requested synthetic Taglish words after punctuation/case/spacing normalization; it was not byte-exact. Native analysis completed in 44,148 ms (250 raw characters logged); drafting completed in 36,345 ms (51 characters logged). A 51-character editable reply was retained privately from Akma UI. Miguel reports wrong language or intent, so Taglish fidelity is FAIL. The precise semantic subtype is not inferred from automated wording checks.

Three consecutive offline user attempts completed native analysis and drafting on the same initial debug APK, without a process restart between them. English fidelity PASS; Filipino fidelity FAIL (unsupported English refusal); Taglish fidelity FAIL. Six completed native operations do not establish long-run stability or semantics. The new release APK has separate gates.

At 19:51:07 UTC, process PSS 2,081,471 kB, RSS 2,076,202 kB and SWAP PSS 83,571 kB; thermal status 0; process running. No serious thermal event or native crash was observed during the three-attempt sequence. First-token latency, measured token count, throughput, GPU acceleration and 4 GB compatibility remain NOT TESTED.

## Controlled import interruption and recovery

After the three user attempts ended, Akma alone was stopped. Its original model was renamed into a private backup in the same directory; no model or user data was discarded. Launching the initial debug APK triggered a real bundled import. At 19:56:17 UTC, a 16,515,072-byte partial had been observed. Akma was stopped before publication; the final installed-model destination was absent and the original backup remained intact. Interrupted-import safety is PASS.

Restarting removed the original orphan partial, but no verified model destination appeared during the bounded observation window. Recovery completion is FAIL for the initial APK. Its 60,000 ms initialization deadline is a suspected cause; no direct timeout/error banner was captured, so that cause is not reported as observed fact. The test collector stopped Akma and the original model was restored by rename: 1,597,931,520 bytes, no backup remaining. No app-data clear or unrelated process termination occurred.

Quaternary created a separate read-only validation worktree at exact `3a30711` to build a debug diagnostic variant under the new initialization budget. No tracked engine/UI/coordinator/Gradle/manifest changes are made there. Initial model staging failed because another owner's temporary artifact path had disappeared; the first build is therefore source validation, not a model-enabled artifact claim. The surviving integration asset independently passed exact size/header/SHA checks and is retained by an owned hard link for the subsequent diagnostic build. The frozen release APK's already verified identity is unaffected.

## Independent latest-source validation and diagnostic APK

Quaternary's detached, unmodified-source worktree at `3a3071139b6474c8ba393ccdf800e98be55c2d5a` ran JDK17 Gradle Wrapper with `--no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease`: PASS in 4m9s. Actual JUnit XML: 280 tests, zero failures/errors/skips. Each lint variant: zero errors, 12 warnings. This initial compile/test gate was model-free because the first artifact-staging attempt failed.

After staging the independently verified ignored model by hard link, `:app:assembleDebug` passed in 55s. Quaternary's diagnostic APK is distinct from both the initial debug and frozen release artifacts:

- Source `3a30711`; APK bytes 1,676,716,358; SHA-256 `70939d41efbb159e133395a81dff7440b435aa235a25081788e670340714bb24`.
- Independent embedded model size/header/SHA/uncompressed storage and ARM64 JNI ELF: PASS.
- JDK17 signature validation PASS; signer matches the frozen release and installed app. AAPT: Akma / ph.merd.akma / min30 / target36 / debuggable.
- Actual merged debug manifest guard PASS, with expected debug-only debuggable warning. This debug variant is for app-private diagnostic access, not submission.
- Replacement installation PASS: exit0, 101,870ms; completed 20:16:20 UTC. Installed APK SHA independently read back and matches `70939d41...`. Only the one identified 1,553,141,640-byte generated native cache was removed after stopping Akma; model and user data were preserved.

On this latest-source diagnostic APK, interruption safety again PASS: 46,989,312-byte partial observed; no installed destination after process stop; original model preserved. Restart recovery PASS: the original orphan was removed; a complete verified model was atomically published in an observed 27,678 ms; its SHA matches the trusted owner pin; zero partials remained. This wall time measures the collector's launch-to-publication observation, not native model initialization.

Same-size corruption recovery PASS: after stopping Akma, one byte at offset 4,096 was altered in the newly imported diagnostic copy, preserving its 1,597,931,520-byte length and LITERTLM header. The original backup was a separate inode and remained unchanged. Restart rejected and replaced the corrupt copy in an observed 83,501 ms; the repaired SHA exactly matches the trusted pin. Akma was stopped again and the original model restored; no backup or deliberately corrupt installed model remains. These tests ran on diagnostic APK `70939d41...`, not the non-debuggable release. Full native initialization of the newly imported read-only diagnostic copy was NOT TESTED because testing stopped after integrity publication. The release subsequently initialized the original verified app-private artifact.

Physical insufficient-storage model import is NOT TESTED; the device was not deliberately filled. Meaningful unit tests cover storage refusal, missing files, cancellation and failed replacement. No second user-facing import flow, broad storage permission or network download was introduced.

Evidence head `47f8333` hosted Android/documentation checks PASS: [push](https://github.com/merd-labs/akma/actions/runs/37984771004), [PR](https://github.com/merd-labs/akma/actions/runs/37984775572).

## Current release gate summary

These gates refer only to frozen release SHA `3e9fe18697409195323af83c05286109b68d0bbc13622643c23321b3889672d0` on Pova 2 LE7 / Android 11 / API30 / ARM64, unless another artifact is explicitly named.

| Gate | Result | Evidence or measurement gap |
| --- | --- | --- |
| Release whole-APK and embedded-model integrity | PASS | Independent host verification and installed whole-APK SHA agree |
| Install | PASS | Exit 0; host wall time 105,221 ms |
| Genuine CPU native initialization / Ready | PASS | Native 64,651 ms; Miguel confirms Ready without Retry |
| Activity launch latency | NOT TESTED | Observer timeout at 90 s; no valid Activity timing returned |
| Explicit offline radio state | PASS | Airplane 1; Wi-Fi setting 0 and service disabled; selected mobile-data setting 0 |
| Native English analysis completion | PASS | 47,791 ms; 204 raw characters logged; contents remain private |
| English draft completion and fidelity | PASS | Native draft 37,249 ms; 72-character actual reply retained privately; user confirms intent preserved |
| Three consecutive release attempts | NOT TESTED | Only two release attempts completed before the slot was released |
| Release Filipino fidelity | FAIL | Native analysis 40,746 ms; draft 43,509 ms; user reports wrong language or intent |
| Release Taglish | NOT TESTED | Pending manual request withdrawn before any observed start |
| Ready after a second release process restart | NOT TESTED | Separate full restart pending |
| Release native cancellation and retry | NOT TESTED | Domain regression tests are not physical cancellation evidence |
| Release memory and thermal observations | PASS | Sampled post-init PSS 2,174,437 kB, RSS 2,269,032 kB; thermal 0; no peak-memory claim |
| Interrupted import / restart recovery / same-size corruption | PASS on diagnostic APK only | Exact same source; APK `70939d41...`; publication 27,678 ms, corruption replacement 83,501 ms |
| Physical model-import storage refusal | NOT TESTED | Phone not deliberately filled; meaningful unit cases pass |
| Camon 30 or Infinix exact-release compatibility | NOT TESTED | Requires separate exclusive device gate |
| Native Windows tooling | NOT TESTED | PowerShell on Ubuntu passed; Windows host execution absent |
| First-token latency / measured tokens per second / GPU / 4 GB compatibility | NOT TESTED | No supporting measurements |

## First exact-release genuine response

The first release test input was byte-exact against the requested synthetic English fixture. Native analysis completed in 47,791 ms (204 raw characters logged); native drafting completed in 37,249 ms (72 characters logged). Akma-owned UI capture independently contains the actual 72-character draft. Miguel confirms intent preserved for the requested Reschedule / Professional selection, without invented availability. Reschedule is also visible in the retained UI; tone was not independently visible in that capture. The fidelity PASS is the user's observation, not an automated semantic assertion. Exact text remains private.

At 20:46:50 UTC, the release process reported PSS 2,331,929 kB, RSS 2,448,432 kB and SWAP PSS 156 kB; available system RAM 2,416,648 kB; /data/user/0 free 3,555,036 KiB; thermal status 0. All offline settings were explicitly reverified. These snapshots are not peak measurements or leak guarantees. The second consecutive attempt and final slot release are recorded below.

## Final active attempt and Pova slot release

Miguel directed Quaternary to finish the active attempt, stop additional long scenarios, and switch the final demo gate to Rhence's phone after Primary/Danielle integration. The latest completed attempt was the requested synthetic Filipino interview scenario with Reschedule / Professional. Actual UI input measured 80 characters against the requested fixture's 82; it was not byte-exact and did not match after punctuation normalization. The actual input and output are retained privately; no exact-fixture fidelity claim is made for this release attempt.

Native analysis completed in 40,746 ms (233 raw characters logged); native drafting completed in 43,509 ms (99 characters logged). The actual 99-character editable reply is independently retained from Akma UI. Miguel reports wrong language or intent: fidelity FAIL. The precise subtype is not inferred. Native completion PASS does not make the product response acceptable. End-to-end elapsed time and first-token latency were not measured; the two native operation times exclude the user's selection/confirmation interval.

At 20:51:17 UTC, PSS was 2,306,437 kB, RSS 2,421,704 kB, SWAP PSS 157 kB; thermal status 0; process running. At 20:48:57 UTC, available system RAM was 2,460,492 kB and free storage 3,554,176 KiB; explicit airplane/Wi-Fi/mobile-data gate PASS. The final retained process log contained zero matches for OutOfMemoryError, UnsatisfiedLinkError, JNI DETECTED ERROR, Fatal signal, SIGSEGV or SIGABRT since the release-generation marker. Buffer coverage is incomplete by design; absence is not proof of complete error coverage. No model-operation timeout or serious thermal event was observed in this completed attempt. The earlier 90-second Activity observer timeout remains a separate collection limitation.

The pending Taglish request was withdrawn before any observed start. No further restart, cancellation, matrix, install, app-data deletion or settings changes were performed. Owned host collectors and ADB listeners were stopped, and the exclusive Pova slot was released. The application and verified model were left intact. Rhence's frozen integration APK, exact device identity and exclusive testing slot remain pending; no Rhence device PASS is claimed. Do not reuse the Pova APK's hash or results for a later merged artifact.

Recommended handoff: retain the already-integrated PR #22 bundled integrity seam; let Primary reconcile runtime changes and Danielle's UI through the agreed integration branch; freeze and verify one resulting APK; validate its exact bytes on Rhence only after an exclusive slot is confirmed. Miguel/MERD own review and merge. Quaternary requests no reviewers and performs no merge.
