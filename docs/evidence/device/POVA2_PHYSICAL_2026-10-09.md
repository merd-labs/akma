# Pova 2 physical QA — 2026-10-09

**Physical baseline measured. Genuine Qwen inference NOT TESTED.** No production or inference code changed. The installed APK was retained; no reinstall, downgrade, app-data clear, model download, or network setting change occurred.

## Device slot and installed artifact

Miguel explicitly confirmed an exclusive slot with Elijah and Secondary stopped. Device operations ran from 15:36:56 UTC / 23:36:56 PHT until release at 15:51:58 UTC / 23:51:58 PHT, before the reserved 15:56:56 UTC expiry. [Start announcement](https://github.com/merd-labs/akma/pull/11#issuecomment-6084165788) and [release announcement](https://github.com/merd-labs/akma/pull/11#issuecomment-6084362273) are on PR #11. A new slot is required for any follow-up.

- Physical model: **TECNO LE7**; Android **11 / API 30**; primary ABI **arm64-v8a**, observed by selected-device ADB queries.
- Team-reported physical RAM/storage capacity: **6 GB / 128 GB**. OS readings below do not independently prove marketed capacity.
- Installed application ID: **`ph.merd.akma`**; version **0.1.0 / code 1**; minSdk **30**; targetSdk **36**.
- Installed base APK: **26,156,046 bytes**; SHA-256 **`f358bcd75a7d3319d8a52fca0a2df75abce2a1d9942cfab41e4c1e87ba7cb2af`**.
- APK identity was obtained by pulling only the package's installed base APK into private host storage and hashing actual bytes. APK bytes and private installation paths are not committed.
- This hash equals the current local overlay worktree debug artifact. The exact source revision used for that binary is not independently attested; it is not the older baseline APK recorded in the earlier preflight report.
- Installed archive contains `UnavailableReplyEngine`; inspected `llama`, `LiteRt`, `LiteRT`, and `InferenceSession` DEX markers are absent. Current overlay source selects the unavailable engine. These are limited static facts; no confirmed Qwen-enabled APK, runtime/model hash, or execution backend was supplied.

## Actual hardware and process readings

Snapshots are sequential system samples, not inference metrics. Times below are UTC; add eight hours for PHT.

| Metric | 15:36:56 before launch | 15:44:55 after launch | 15:51:57 final | Unit |
|---|---|---|---|---|
| MemTotal | 5,905,908 | 5,905,908 | 5,905,908 | KiB |
| MemAvailable | 1,883,436 | 2,063,300 | 2,117,684 | KiB |
| Free queried `/data` filesystem | Unavailable in original parser | 8,134,776 | 8,132,232 | KiB |
| App total PSS | Unavailable | 69,763 | 83,881 | KiB |
| Battery temperature | 265 (26.5 °C) | 271 (27.1 °C) | 269 (26.9 °C) | deciC |
| Android thermal status | 0 | 0 | 0 | reported status |

An additional direct `df -k /data` probe inside the slot reported **8,133,904 KiB available**, **113,454,048 KiB filesystem size**, and **93% used**; its exact timestamp was not captured. OEM output labels this filesystem `/data/user/0`. The initial collector accepted only `/data`, producing an honest blank value. Both collectors now accept the observed OEM label; the two later snapshots capture actual free space. This does not change the device or allocate storage.

A separate post-launch memory query reported **50,452 KiB total PSS**. These samples do not establish peak process memory, memory pressure, inference allocation, or lack of transient OOM. Battery temperature is not CPU/GPU temperature. Thermal status 0 during baseline activity does not establish behavior under model load.

Reviewed artifacts: [before](pova2-2026-10-09/baseline-before.tsv), [after](pova2-2026-10-09/baseline-after.tsv), [final](pova2-2026-10-09/baseline-final.tsv). Only allowlisted values are retained; serials, tokens, messages, raw dumps, APKs, and weights are excluded.

## Activity, overlay, and timing

- Package presence, version metadata, process presence, and resumed `MainActivity` were observed. Overlay permission mode is **allow**.
- Initial `am start -W` probe hit its **30-second host timeout**. No completion timing was captured; cold-process/cache conditions were not established. A subsequent query showed Akma resumed. This is a timing-probe failure, not a proven crash or ANR.
- [#19 — initial launch timing wait](https://github.com/merd-labs/akma/issues/19) records the command, timeout limit, successful repeat, and uncertain cause for Secondary/Miguel.
- Repeat launch at **15:50:19 UTC** returned **exit 0**, **Status ok**, **LaunchState WARM**, **Android TotalTime 274 ms**, **WaitTime 297 ms**. Host command wall time was **380.709 ms**, measured with `time.perf_counter()` around the ADB command. `ThisTime` was absent. These are Activity launch/wait measurements, not first UI frame, model initialization, or reply latency.
- While Akma Activity was not resumed, its foreground overlay service existed and one application-overlay window was visible. A later background snapshot showed **192 × 192 px**, consistent with the bubble. No third-party UI/message content was inspected or retained.
- Miguel reports that an expanded panel remains open when leaving Akma and copying in Messenger does not automatically reopen the assistant. That earlier expanded-panel state was not independently reproduced by the bubble snapshot. Current workflow uses manual bubble expansion, explicit Paste, and Close panel; clipboard monitoring is not authorized.
- Follow-up: [#18 — collapse/reopen UX decision](https://github.com/merd-labs/akma/issues/18). Paste keyboard focus, clear-on-close, manual Copy, and repeated toggles were not independently measured during this session.

## Offline and genuine inference gates

Read-only global indicators reported **airplane_mode_on=0**, **wifi_on=1**, **mobile_data=1**. No Settings changes were made. This fails the required airplane-mode/disabled-radio condition at the observation time. These flags alone would not prove all-SIM/alternate-network isolation even if changed.

No genuine generation was attempted without a confirmed model-enabled APK. Exact prompts, actions, tones, and actual outputs therefore do not exist for this session. Planned matrix remains initial English plus consecutive warm English, Filipino, and Taglish HR-reschedule attempts, each Professional. Future genuine replies stay in private artifacts; reviewed hashes and fidelity findings belong in committed evidence.

| Gate | Result | Evidence or limit |
|---|---|---|
| Exclusive slot / authorized physical device | PASS | Miguel confirmation; bounded slot and announced release |
| LE7 / Android 11 API 30 / ARM64 identity | PASS | Selected-device property readings |
| System memory / free storage | PASS | Actual KiB values above; OEM parser corrected |
| Installed package and actual base APK identity | PASS | Package/version query, pull, SHA-256, manifest metadata |
| Fresh install/reinstall | NOT TESTED | Existing experimental installation retained |
| Activity resumed / repeat warm launch | PASS | Activity query; exit 0 and actual warm launch timings |
| Initial launch timing acquisition | FAIL | 30-second host timeout; no cold-start measurement |
| Overlay permission / visible background overlay service | PASS | Permission allow, visible window and foreground service |
| Expanded panel auto-collapse / clipboard-triggered reopening | NOT TESTED | Operator concern; intended behavior needs owner decision in #18 |
| Paste keyboard / clear-on-close / manual Copy / repeated toggles | NOT TESTED | No independent command-backed result |
| Airplane-mode disabled-radio prerequisites | FAIL | Airplane 0, Wi-Fi 1, mobile data 1 at observation |
| Genuine Qwen initialization and first generation | NOT TESTED | Confirmed model-enabled APK absent |
| Three consecutive English/Filipino/Taglish offline attempts | NOT TESTED | No real generation attempted |
| Exact action/tone/output fidelity and invented commitments | NOT TESTED | No genuine output |
| First-token/result latency, response duration, token throughput | NOT TESTED | No runtime output/timing or trusted token counts |
| Inference PSS/peak memory, native/JNI crash, OOM and thermal behavior | NOT TESTED | Baseline snapshots do not exercise inference or audit historical failures |
| GPU execution | NOT TESTED | No backend execution evidence; no claim |

## Reproduction, local checks, and owner follow-up

Reserve a new slot first. Use the existing collector with explicit private serial selection, alias `pova2`, phase label, unique private output, and `--slot-confirmed`. Revalidate identity before accepting readings. Review TSV values before copying artifacts into Git.

Command templates below require private substitution; never paste a real serial or installation path into public logs:

```text
adb -s <REDACTED> shell getprop ro.product.model
adb -s <REDACTED> shell getprop ro.build.version.sdk
adb -s <REDACTED> shell getprop ro.product.cpu.abi
adb -s <REDACTED> shell df -k /data
adb -s <REDACTED> shell dumpsys meminfo ph.merd.akma
adb -s <REDACTED> shell am start -W -n ph.merd.akma/ph.merd.akma.MainActivity
adb -s <REDACTED> shell settings get global airplane_mode_on
adb -s <REDACTED> shell settings get global wifi_on
adb -s <REDACTED> shell settings get global mobile_data
```

Capture command output privately; retain only approved fields. Catch subprocess timeouts without logging command argument arrays. One initial diagnostic exception exposed the selector in private tool output; no raw exception or identifier is copied into committed evidence. Never infer a measured startup time from a timeout limit.

- `bash -n scripts/bench/collect.sh`: exit 0 after OEM correction.
- `python3 scripts/bench/test_collectors.py`: exit 0; **8 test methods in 128.821 seconds**, both Bash and PowerShell 7.6.6, including `/data` and `/data/user/0` fixtures. Synthetic transport values are not handset readings.
- PowerShell parser validation and both collector help commands: exit 0. Exact parser command is recorded in `TOOLING_VALIDATION.md`; Windows-native execution remains NOT TESTED.
- Three physical collector invocations: exit 0. Initial free-storage field unavailable; corrected after/final invocations record real values.
- Android build/tests were not rerun locally: production code unchanged and no concurrent Gradle build occurred during the device slot. Hosted results must be checked on the pushed head separately.

Elijah supplies the real Qwen-enabled artifact and model/runtime/settings provenance. Secondary/Miguel resolve #18 and diagnose the initial launch wait without assuming an ANR or changing the manual clipboard contract. Miguel owns shared CI correction. QA does not request or assign reviewers. Recommended integration order: passing CI/bootstrap under Miguel, domain safety and overlay through their owners, genuine runtime integration, then final offline evidence on that exact integrated APK. No branch merge is needed to reserve a phone.
