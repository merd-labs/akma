# Pova 2 QA session — 2026-10-09

**Session status: waiting for exclusive handoff and a confirmed model-enabled APK.** No physical handset shell commands, installation, launch, setting changes, or generation have been performed in this session. All handset measurements below remain unmeasured.

## Observations and coordination

- Repository: `merd-labs/akma`; branch: `test/device-benchmark`; existing draft PR: [#11](https://github.com/merd-labs/akma/pull/11).
- Host inspection at 2026-10-09 12:44 UTC / 20:44 PHT: ADB returned exit 0 and listed one non-emulator candidate in state `device`, plus an emulator. Serial and transport identifiers were withheld. This establishes ADB visibility/authorization for a candidate, not its handset identity.
- Target hardware supplied by the team: Tecno Pova 2 LE7, Android 11/API 30, Helio G85, physical 6 GB RAM, 128 GB storage capacity. These are not observations from this session.
- Exclusive slot: **NOT CONFIRMED**. The owner previously reported that Elijah had not handed off the device.
- [Request to Elijah](https://github.com/merd-labs/akma/pull/11#issuecomment-6081155233) posted at 12:45:23 UTC / 20:45:23 PHT. It requests a 20-minute window, explicit start/release times, model-enabled artifact/provenance, and agreement before replacing an active experiment's APK.
- Collector baseline reviewed: `22e632d7fe6a7bd2dc24ece599de811339f9da4d`. Existing scripts are unchanged. Slot confirmation is a human attestation, not an automatic device lock.

## APK identity measured on the host

Metadata was extracted with Android Build Tools 35.0.0 `aapt dump badging`; the command returned exit 0 for both candidates. SHA-256 was computed from actual file bytes. These facts do not prove installation or execution on API 30.

| Field | Baseline candidate | Overlay candidate |
|---|---|---|
| Local artifact role | Main checkout debug APK | Overlay worktree debug APK |
| Application ID | `ph.merd.akma` | `ph.merd.akma` |
| Declared minSdk | 30 | 30 |
| Launch Activity | `ph.merd.akma.MainActivity` | `ph.merd.akma.MainActivity` |
| Size | 26,060,074 bytes | 26,149,102 bytes |
| SHA-256 | `7e1a003276f416513cc91168b5fd7739308069c8168f81df78219c6e90350f83` | `ab6feebbc168454961409e136b7049a9787029b27efe94bbe3a69c1c46aed02c` |
| Artifact-to-source/model provenance | NOT VERIFIED | NOT VERIFIED |
| Installed on Pova 2 | NOT TESTED | NOT TESTED |

Both archives contain an `UnavailableReplyEngine` class marker. Neither contains the inspected `llama`, `LiteRt`, `LiteRT`, or `InferenceSession` DEX strings. Matching checkout source selects `UnavailableReplyEngine`. This is a limited static inspection, not proof about all possible runtime paths or the exact source revision used to build the APK. Neither artifact is accepted as confirmed model-enabled evidence.

The canonical baseline candidate is `app/build/outputs/apk/debug/app-debug.apk` in the main Akma checkout. The overlay candidate is the same relative path in the overlay worktree. APK binaries remain outside Git. No APK was installed or substituted for Elijah's experiment.

## Gate report

`PASS` requires the stated condition to have been observed. `FAIL` identifies an executed check that failed. `NOT TESTED` never becomes a pass because a host build, metadata check, or emulator succeeded.

| Gate | Status | Actual evidence or gap |
|---|---|---|
| Candidate appears as authorized ADB device | PASS | Inventory exit 0; candidate state `device`; identifiers withheld |
| Exclusive Pova 2 slot | NOT TESTED | Requested; no explicit handoff recorded |
| Physical LE7 identity, API 30, and ABI | NOT TESTED | No handset shell queries |
| Current MemTotal / MemAvailable | NOT TESTED | No values in KiB measured |
| Current free `/data` storage | NOT TESTED | No value in KiB measured |
| Local APK metadata and SHA-256 | PASS | Actual host values above; declared minSdk 30 |
| APK installation on physical API 30 | NOT TESTED | Waiting for slot and agreed APK |
| APK launch and usable Activity | NOT TESTED | No launch performed |
| Confirmed model-enabled APK/runtime/model | NOT TESTED | No validated artifact supplied |
| Model initialization | NOT TESTED | No milliseconds measured |
| First genuine generation | NOT TESTED | No milliseconds or actual reply recorded |
| Three consecutive warm offline generations | NOT TESTED | No inference attempted |
| Wi-Fi and all SIM mobile data disabled | NOT TESTED | No Settings inspection or state change |
| Selected action/tone matches real reply | NOT TESTED | No generated reply available |
| First-token latency and token throughput | NOT TESTED | No trustworthy runtime measurements; no estimate |
| Process memory during generation | NOT TESTED | No PSS in KiB sampled; peak memory unmeasured |
| Recoverable errors / timeout / cancellation | NOT TESTED | No runtime exercised |
| Crash/OOM, thermal, and OEM lifecycle behavior | NOT TESTED | No warnings, temperatures, thermal status, or recovery observed |
| Actual GPU inference backend | NOT TESTED | No execution proof |
| Existing hosted Android build/test/lint | PASS | Prior head `22e632d`: [PR job](https://github.com/merd-labs/akma/actions/runs/37926563256/job/113806803523) succeeded; not handset evidence |
| Existing hosted documentation gate | FAIL | Prior head `22e632d`: [job](https://github.com/merd-labs/akma/actions/runs/37926563256/job/113806803262) fails on unchanged historical whitespace |

## Reproduction after handoff

1. Obtain Elijah's explicit slot, release time, and selected APK agreement. Privately select the authorized handset serial; never include it in committed commands or terminal transcripts. Confirm physical LE7 identity and API 30 before accepting results. An unexpected device/API is a failed identity gate, not a Pova 2 pass.
2. Run the existing collector with `--device pova2`, an explicit serial, a unique private output file, and `--slot-confirmed`. Review only the allowlisted TSV fields. Record memory/storage in KiB, battery temperature in deciC, thermal status as reported, and package PSS where available. Missing readings remain unmeasured.
3. Recompute SHA-256 immediately before installation. Record the selected file size, package, Activity, minSdk, build provenance, and hash. Use `adb -s <REDACTED> install -r <AGREED_APK>` only after agreement. A version/signature conflict ends installation; never uninstall, clear app data, or replace an experiment silently.
4. Launch the verified Activity with `adb -s <REDACTED> shell am start -W -n ph.merd.akma/ph.merd.akma.MainActivity`. Capture stdout/stderr privately and extract only status and numeric launch timings for review. Record the Activity observation separately. Android launch timing is not model initialization or time to usable UI.
5. Await Elijah's confirmed model-enabled APK and model/runtime provenance before inference. An unavailable engine leaves generation gates NOT TESTED. Do not fabricate success with fixture output.
6. After provisioning, the operator disables Wi-Fi and every SIM's mobile data manually. Confirm Settings state and read-only global indicators (`wifi_on`, `mobile_data`) before and after the run; those indicators alone are insufficient for all OEM/multi-SIM cases. Record the operator's all-SIM verification and absence of tethering/alternate network connections. Ambiguous conditions leave the offline gate NOT TESTED.
7. Use fixture `HR-RESCHEDULE-01`: a synthetic interview invitation without personal details; select Reschedule and Professional. Keep the actual prompt and generated replies in a private local artifact outside Git. Commit only the fixture ID, selected action/tone, observed fidelity/no-invented-commitments judgments, timing numbers, and gate result.
8. Separate app readiness, explicit model initialization, analysis, first generation, and three consecutive warm generations. Define stopwatch or runtime timing boundaries/resolution. Take before/after snapshots for each run. Snapshot PSS is not peak memory. A final-only UI cannot establish first-token latency; missing token counts cannot establish tokens per second.
9. Observe responsiveness and operator-controlled background/foreground recovery. Do not inspect third-party messages, clipboard contents, screenshots, or raw crash logs. Stop on a serious thermal warning, unsafe heat, two OOM failures, slot expiry, or another owner's request. Record executed failures and leave remaining gates NOT TESTED.
10. Release the slot, review every artifact for identifiers/private text, and update this report with actual values and units. Propose production changes to the relevant owner; modify documentation only.

## Local commands and next owner

- `bash -n scripts/bench/collect.sh`: exit 0 in this session.
- `bash scripts/bench/collect.sh --help`: exit 0; usage printed. No collector transport calls were made.
- `aapt dump badging <LOCAL_APK>`: exit 0 for both candidates; only selected package/minSdk/Activity metadata was emitted.
- Python SHA-256 and archive marker inspection: completed successfully; no message contents, identifiers, or raw DEX/log output emitted.
- `adb devices`: exit 0; raw inventory was captured in memory and replaced with redacted state summaries.
- GitHub inspection confirmed PR #11 remains open/draft, based on bootstrap; human reviewer `jairuss0` is requested.

**Next owner:** Elijah provides the exclusive slot and confirmed model-enabled APK. Miguel owns the shared CI whitespace correction. No physical performance or offline-inference gate can pass from the current evidence.
