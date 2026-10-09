# Windows 11 Pro 25H2 — Android baseline

Windows commands are documented but have not been run during the Ubuntu bootstrap. No WSL is required. From PowerShell in the repository root, audit installed tools first:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-windows.ps1
```

The execution-policy override applies only to this reviewed audit process. Do not change machine-wide policy.

## Pinned build tools

Use JDK 17, Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, Compose BOM 2025.10.00, Activity Compose 1.11.0 and JUnit 4.13.2. Compile/target SDK is 36, Build Tools is 35.0.0, and minSdk is 30. The wrapper scripts and JAR are checked in; no global Gradle installation is required.

If missing, install Git, GitHub CLI and Android Studio from their official distributions or reviewed `winget` packages:

```powershell
winget install --id Git.Git -e
winget install --id GitHub.cli -e
winget install --id Google.AndroidStudio -e
```

Use an existing JDK 17 or install JDK 17 from an official vendor. Select it as Android Studio's Gradle JDK. Select the same JDK in PowerShell using its actual location:

```powershell
$env:JAVA_HOME = 'C:\REPLACE_WITH_ACTUAL_JDK_17_PATH'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
java -version
.\gradlew.bat --version
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Adjust SDK location if needed. Install Android SDK Platform 36, Build Tools 35.0.0 and platform-tools through Android Studio SDK Manager. Accept the SDK licenses as the operator. NDK/CMake is not needed for this baseline. Keep SDK paths, `local.properties`, credentials and IDE state out of Git. Dependency provisioning requires Internet access; app inference remains local and unimplemented.

## Device installation

Install the phone's USB driver if needed. Enable USB debugging only on an authorized device.

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n ph.merd.akma/.MainActivity
```

The baseline displays **Model unavailable**. Installation and launch must be observed on Pova 2; building alone does not prove phone or runtime compatibility. See `BOOTSTRAP_VERIFICATION.md` and `ADB_POVA2.md`. Hide device serials when sharing logs.

## Optional tooling

Miguel owns Spec Kit and Matt Pocock Skills integration. Defer setup until baseline/contract merge; inspect installed CLI help and keep one installation mechanism per agent. No tooling initialization is needed for the Android build. See `SPECKIT_AND_SKILLS.md`.
