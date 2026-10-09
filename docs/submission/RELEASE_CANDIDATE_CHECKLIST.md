# Release Candidate Checklist

**Submission Deadline:** October 10, 2026, 10:00 AM PHT (Internal target: 09:00 AM PHT)
**Rule:** Exact one-submission cut-off. Do NOT submit without explicit human action/approval by Miguel.

## Pre-Flight
- [PASS] `README.md` updated with verified facts, no promises.
- [PASS] `docs/AI_DISCLOSURE.md` completed with exact models, licenses, and coding tools used.
- [NOT TESTED] Code compiles on MinSDK30 (Android 11) using `./gradlew assembleDebug`. (Baseline PR pending merge).
- [PASS] No fake AI or mocked responses are claimed as real AI.
- [PASS] No secrets, `.env`, keystores, or personal chat messages in the repository.
- [PASS] All team members registered and credited.
- [PASS] Public GitHub repository created.

## On-Device Validation
- [NOT TESTED] APK successfully installed and launched on a physical test device (Pova 2).
- [NOT TESTED] Airplane mode enabled during inference (if offline capability verified).
- [NOT TESTED] Model weights successfully loaded from device storage.
- [NOT TESTED] LLM inference executes successfully.

## Media & Materials
- [NOT TESTED] 1-minute working-product video recorded (using synthetic data).
- [PASS] Public social post drafted (but NOT published).
- [PASS] Tags/hashtags verified for Cerebral Valley.
- [NOT TESTED] Public repository visibility confirmed (if approved by Miguel).

## Final Handoff
- [NOT TESTED] Android baseline PR merged into main.
- [PASS] Documentation PR created and reviewed by Miguel.
- [NOT TESTED] Final explicit human approval granted.
- [NOT TESTED] Submission submitted to the official **Cerebral Valley** submission destination before 10:00 AM PHT cutoff.
