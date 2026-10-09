# Tecno Pova 2 (6 GB / 128 GB) — read-only hardware verification

User-reported: Tecno Pova 2 LE7, Android 11/HiOS 7.6, Helio G85, physical 6GB RAM and 128GB storage capacity. **Actual available memory and free storage are unknown**. Do not claim 4GB compatibility.

## Ubuntu Bash or Windows PowerShell

Connect your own Android phone with USB debugging authorized. If multiple phones are connected, use `adb -s <SERIAL> shell ...` with the specific serial; redact serial before posting logs.

```text
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell "grep -E 'MemTotal|MemAvailable' /proc/meminfo"
adb shell df -h /data
```

Expected broad values: SDK `30`, ARM64 `arm64-v8a`, MemTotal roughly in the 6 GB class (Android reserves some), and free `/data` reported independently of total 128GB capacity. Stop if device reports wrong ABI/API or low free space; address the mismatch before loading models. Never run a heavyweight benchmark for many minutes without watching temperature and responsiveness.

## Controlled offline inference verification

- Download model on a trusted connection and import to app-private storage; record source/revision/license/SHA-256. **Never commit model weights, private messages, or tokens**.
- Disable both Wi-Fi and cellular data; confirm local runtime does not make network calls. On Android, airplane mode can be toggled manually.
- Use a synthetic HR invitation; choose RESCHEDULE. Expected: polite message requesting an alternative, **no fabricated confirmed attendance**.
- Time model initialization and short reply generation, repeat 3 times; record crashes/ANRs/memory pressure and warm/cold behavior.
- Check model unload/Activity reopen and long message failure path. Use Camon 30 as Android 14 secondary smoke test.
