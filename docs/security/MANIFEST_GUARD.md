# Merged-manifest security guard

Files: `scripts/security/ManifestGuard.java`, `scripts/security/manifest-policy.txt`,
`scripts/security/check-merged-manifest.sh`, `scripts/security/check-merged-manifest.ps1`,
tests `app/src/test/java/ph/merd/akma/security/ManifestGuardTest.kt` + fixture `app/src/test/resources/manifests/merged-clean-debug.xml`.

Why the **merged** manifest: libraries add permissions and components during manifest merge. The source manifest
(`app/src/main/AndroidManifest.xml`) can look clean while the APK declares `INTERNET`.

## Run it

Needs only the JDK already required by the project (JDK 17; newer JDKs also work — verified with 17 and 27).

```bash
./gradlew :app:processDebugManifest
scripts/security/check-merged-manifest.sh            # debug variant
scripts/security/check-merged-manifest.sh debug --release   # additionally fails on debuggable=true
```

```powershell
.\gradlew.bat :app:processDebugManifest
.\scripts\security\check-merged-manifest.ps1
.\scripts\security\check-merged-manifest.ps1 -Release
```

Direct form (any OS): `java scripts/security/ManifestGuard.java --policy scripts/security/manifest-policy.txt <merged AndroidManifest.xml>`.
Exit `0` pass, `1` policy violation (message names the element and how to fix or approve it), `2` unreadable input / usage.

## What it enforces

| Rule | Fails when |
|---|---|
| permissions | any `uses-permission` / `uses-permission-sdk-23` not in the policy. Specific text for INTERNET, ACCESS_NETWORK_STATE, SMS, contacts, mic, camera, storage, boot, install, query-all-packages, accessibility/notification-listener/IME bind permissions |
| exported components | `android:exported="true"`, or no attribute plus an intent-filter, on anything not listed under `exported` in the policy; guarded components (ProfileInstallReceiver needs `android.permission.DUMP`) fail if the guard is missing; exported provider without any permission |
| platform services | any component with a forbidden bind permission, accessibility/notification-listener/input-method/autofill/VPN/call-screening action or their meta-data |
| application | `allowBackup` not `false`, `usesCleartextTraffic="true"`, `sharedUserId`, custom `<permission>` that is not signature level; `debuggable`/`testOnly` = **warning**, **error with `--release`** |
| input | DOCTYPE/entities are rejected (XXE-safe parser) → exit 2 |

It never edits a manifest and never removes a permission. Approving something new is a reviewed edit of `manifest-policy.txt`
(the current policy is exactly the permissions and exported components Akma uses today; `ManifestGuardTest` asserts the policy
never approves INTERNET, ACCESS_NETWORK_STATE or accessibility/notification/IME binding).

## Tests (run inside the existing `:app:testDebugUnitTest`)

`ManifestGuardTest` runs the same program through `ProcessBuilder(java.home/bin/java …)`:
clean fixture and the **real merged manifest** pass; a copy of the real merged manifest with `INTERNET` injected fails (positive
regression: `forbiddenPermissionInjectedIntoRealMergedManifestIsDetected`); 13 hostile variants (INTERNET, network state, SMS,
accessibility permission and service, notification listener, exported service, implicitly exported receiver, open provider, unguarded
ProfileInstallReceiver, cleartext, backup on, shared UID, weak custom permission) each fail with the expected message; debuggable is a
warning in debug and an error with `--release`; DOCTYPE and missing files exit 2.
`testDebugUnitTest` already depends on `processDebugManifest` (checked with `--dry-run`), so the merged manifest exists when the test runs.

## CI (Primary owns the workflow)

`docs/security/patches/ci-manifest-guard.patch` adds an explicit step after the Gradle gate (validated with `actionlint`; applies to
`main`). It is optional: the JUnit test already runs the guard inside the existing CI step. The step makes the check visible as its
own line in the Actions log. For a release/demo APK add a second run with `--release` against the release variant's merged manifest.

## Limits

- It checks the manifest, not the dex: a library could still contain code that tries to use a network if a permission were added later;
  that is exactly what the permission check blocks. Native code cannot open sockets without INTERNET.
- Windows: the `.ps1` wrapper was executed with PowerShell 7 on Ubuntu and parse-checked; a native Windows run is **NOT TESTED**.
