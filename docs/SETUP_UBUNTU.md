# Ubuntu 24.04.5 LTS — read-only audit before changes

Run from repository root:

```bash
bash scripts/verify-linux.sh
```

Required to build Android: Git, Java JDK compatible with pinned AGP (often JDK 17), Android Studio, Android SDK platform-tools (`adb`), SDK platform matching `compileSdk`, commandline-tools and Gradle wrapper once created. NDK/CMake only if llama.cpp/JNI selected. Docker/backend not necessary for this app. Use your existing SDK installation; avoid duplicate SDK or forced mise/toolchain upgrades.

After bootstrap merges:

```bash
./gradlew --version
./gradlew --no-daemon assembleDebug testDebugUnitTest
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A Gradle wrapper cannot work until the Android bootstrap agent generates it. If `adb` lists unauthorized device, unlock phone and authorize USB debugging. Never share log output containing credentials.

## Specify and skills (optional team workflow)

From your *local* repo, only if Python 3.11+, uv and installed agents are already working:

```bash
uv tool install specify-cli
specify --help
specify init --here --integration codex --script py --force
```

**Warning:** Spec Kit `--force` may modify existing files; commit/back up and review diff before/after. For additional agent support, inspect `specify integration --help` and official docs; do not blindly run setup multiple times. Use `--script py` for uniform cross-platform Python scripts (verify your installed release supports it).

For Matt Pocock Skills, use one supported mechanism per agent; see `docs/SPECKIT_AND_SKILLS.md`. Avoid committing dozens of unused skills during the sprint.
