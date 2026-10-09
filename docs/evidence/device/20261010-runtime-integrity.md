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

## Installed APK identity

Read from the device at 18:28:50 UTC, without installing or launching a replacement:

- Application ID: `ph.merd.akma`; versionCode 1; versionName 0.1.0; minSdk 30; targetSdk 36.
- APK size: 26,109,226 bytes.
- APK SHA-256: `9a0b5e7ee6431f05430da154086884ce4612a91bb6292d3e5e9d3e42c9342602`.
- Bundled `.litertlm` asset count: 0. This APK cannot establish the bundled-model gate.

## Model and release gates

PR #26 head `ae7f0946c6a6be26fd159650900da6769eb81008` pins the Qwen filename/revision/size/SHA documented in `docs/model-provisioning/INTEGRATION.md`. The published pinned artifact SHA matches the owner metadata. That metadata check is PASS; downloaded-byte verification is a separate gate. An existing host partial file measured 853,168,128 bytes, so it failed the expected 1,597,931,520-byte size check and was not used. A separate private download was started; no weights are committed.

| Gate | Result | Evidence or gap |
| --- | --- | --- |
| Downloaded complete model integrity | NOT TESTED | Download pending full size/header/SHA validation |
| Exact combined APK embedded model integrity | NOT TESTED | Integration owner has not supplied a frozen model-enabled APK |
| Exact combined APK install/launch on API 30 | NOT TESTED | Current observed APK is the baseline above |
| Physical model provisioning and native loading | NOT TESTED | Requires the combined artifact |
| Cold/warm initialization and process restart | NOT TESTED | No genuine model initialized in this slot |
| Three English/Filipino/Taglish offline attempts | NOT TESTED | No model generation attempted |
| Actual action/tone/output fidelity | NOT TESTED | No model output observed |
| Import interruption and corruption recovery on phone | NOT TESTED | Unit fixtures do not establish physical behavior |
| Inference PSS, latency, OOM/JNI/native/thermal behavior | NOT TESTED | Preflight snapshots cannot establish these results |
| Same frozen APK on Camon 30 | NOT TESTED | PR #26's earlier CL6/API36 generation used a different APK; mobile-data disablement was not recorded |
| Native Windows tooling execution | NOT TESTED | PowerShell-on-Ubuntu tests are separate |

## Reproduction and ownership

Use the pinned metadata and JDK 17 Bash/PowerShell commands in `docs/model-provisioning/INTEGRATION.md`. Run the artifact verifier before and after packaging. Record exact combined source commit, APK hash, signer and manifest identity, then reserve an exclusive device slot. Every ADB command must use an explicit privately stored serial selector. Verify airplane 1, Wi-Fi 0 and mobile_data 0 before testing real inference. Do not clear app data or bypass incompatible signing.

Measure total startup plus provisioning/hash/native initialization separately. Record response latency and PSS snapshots with timestamps; do not infer first-token latency or tokens/s from character counts. Capture only sanitized errors and measurements. Keep actual synthetic prompts, chosen action/tone and generated outputs in private evidence; commit fidelity findings without content. No GPU or 4 GB compatibility claim is supported.

Primary owns the combined adapter/Gradle/UI bridge; the domain reliability owner owns runtime recovery. Quaternary owns provisioning, artifact tooling and physical measurements. No owner engine/UI/coordinator/manifest/Gradle file is changed by this evidence branch. No reviewers are assigned and no merge is performed.
