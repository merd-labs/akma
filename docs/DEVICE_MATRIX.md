# Hardware baseline (provided by MERD; verify with ADB)

| Device | Android | CPU/GPU | RAM | Role |
|---|---|---|---|---|
| Tecno Pova 2 LE7 | 11, API 30, HiOS 7.6 | Helio G85, Cortex-A75/A55, Mali-G52 MC2 | 6 GB physical RAM confirmed; 4 GB variant not tested | Primary acceptance gate; **128 GB storage capacity** confirmed (free space unknown); eMMC 5.1; 60 Hz |
| Infinix Zero 5G X6815B | 11, API 30, XOS 10 | Dimensity 900, Cortex-A78/A55, Mali-G68 MC4 | 8GB | Performance comparison; 120 Hz |
| Tecno Camon 30 CL6 | 14, API 34, HiOS 14 | Helio G99 Ultimate, Mali-G57 MC2 | 8GB+ | Android 14 compatibility, 120 Hz |

These specifications were **provided by the team**, not independently verified. Do not infer Vulkan/GPU inference availability from graphics API support alone. Rendering at 120Hz is not a requirement for inference speed; avoid UI stalls.

ADB (Bash / PowerShell):
```
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell cat /proc/meminfo
adb shell df -h /data
```

A Pova 2 **6GB** test does NOT prove that its 4GB sibling variant will run the model. Record battery, model load time, warmed generation seconds and app memory (where measurable); use the same prompt/settings on all phones. Disable Wi-Fi/mobile data for actual inference smoke test. Beware HiOS/XOS background restrictions.
