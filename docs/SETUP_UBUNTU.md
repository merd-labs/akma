# Ubuntu 24.04.5 LTS — Android baseline

Run `bash scripts/verify-linux.sh` from the repository root before changing tools. The bootstrap host runs Ubuntu 24.04.5. It has Android Studio and the required SDK already installed; no global upgrades are needed.

## Pinned build tools

Use JDK 17, Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, Compose BOM 2025.10.00, Activity Compose 1.11.0 and JUnit 4.13.2. Compile/target SDK is 36, Build Tools is 35.0.0, and minSdk is 30. The genuine Gradle wrapper is checked in; a global `gradle` command is not required. NDK/CMake is not needed until an inference implementation requires it.

The audited shell defaults to Java 27. Select the installed JDK 17 for this shell, without changing global tool settings:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Android/Sdk"
java -version
./gradlew --version
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Use your actual JDK/SDK locations if they differ. In Android Studio, choose the same JDK 17 as the Gradle JDK. Keep `local.properties`, IDE state and machine-specific Java paths out of Git. Network access is needed to provision dependencies and the wrapper distribution; it is not app inference.

If SDK packages are missing, install only the required packages through SDK Manager, or use a reviewed local command after accepting the SDK licenses:

```bash
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
```

## Device installation

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ph.merd.akma/.MainActivity
```

An unauthorized device requires the owner to unlock it and authorize USB debugging. The baseline displays **Model unavailable**. A successful build does not prove phone compatibility or local inference. See `BOOTSTRAP_VERIFICATION.md` and `ADB_POVA2.md` for evidence and the device gate. Hide device serials when sharing logs.

## Optional tooling

Miguel owns Spec Kit and Matt Pocock Skills setup. Defer initialization until baseline/contract merge. Installed Specify 1.1.1 supports the following form, but do not run it merely to build the app:

```bash
specify init --here --integration codex --script py --force --non-interactive
```

`--force` can overwrite existing files; review generated changes on an isolated branch. Use one Skills installation mechanism per agent, with no duplicate project configuration. See `SPECKIT_AND_SKILLS.md`.

Compatibility sources: [AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes), [Kotlin Gradle plugin](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).
