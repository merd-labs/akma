# Model/runtimes feasibility gate — fill using observations

**Do not claim this gate has passed until evidence exists.** Priority: on-phone inference > overlay > polish.

## 🖥️ Desktop Model Research (Status: PASS)
Based on desktop verification (PR #16), we tested Python LiteRT-LM prompt labs for Akma workflows.

- **Primary Candidate:** Qwen2.5 1.5B Q8 `.litertlm`
  - **Local artifact size:** 1,597,931,520 bytes
  - **Runtime failures:** 0
  - **Generation Latency (Desktop):** ~2.27s
  - **Result:** Provisional leading candidate based on JSON parsing and intent-following checks. Exact model provenance, source URL, and SHA-256 are pending integration documentation.
- **Secondary Candidate:** Gemma 3 1B Q4 `.litertlm`
  - **Local artifact size:** 584,417,280 bytes
  - **Generation Latency (Desktop):** ~0.76s
  - **Result:** Fast, but produced repetitive responses and failed strict Taglish/Filipino validation in desktop probes.

*Note: Qwen Q8 vs Gemma Q4 is not a controlled quantization comparison. No human quality scores have been finalized. Desktop pass does NOT guarantee Android pass.*

## 📱 Physical Android Integration (Status: NOT TESTED)
Do not claim any Android integration works until proven on the physical device.

| Check | Status / observed value |
|---|---|
| Handset / hardware | Tecno Pova 2 LE7; Android 11 API 30; 6GB RAM, 128GB capacity (user reported); verify using ADB |
| Free storage / MemAvailable | **NOT MEASURED** |
| Model repo URL/revision/license acceptance | **NOT SELECTED** (Pending Android verification) |
| Filename, bytes, SHA-256 | **NOT SELECTED** |
| Runtime dependency version/backend | **NOT SELECTED** |
| Load time after cold process launch | **NOT MEASURED** |
| Short HR reschedule draft latency | **NOT MEASURED** |
| Peak device process memory | **NOT MEASURED** |
| Three back-to-back generations | **NOT TESTED** |
| Airplane mode (Wi-Fi + mobile data OFF) | **NOT TESTED** |
| HR reschedule preserves user intent | **NOT TESTED** |

Suggested pass target: a truthful, useful short response ≤30 seconds **after warm-up**, 3 successful repeated runs, no crash/ANR, phone not swapping uncontrollably. This is an internal benchmark, not a product guarantee.
