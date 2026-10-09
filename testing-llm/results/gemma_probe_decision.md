# Gemma 3 1B Q4 focused prompt check

The original 19-case Gemma run is preserved in `gemma_results.json`. This check ran the same three scenarios with the original prompt and a compact Gemma prompt using the same local `.litertlm` artifact, CPU backend, temperature 0, seed 42, and output limits. Both tasks used fresh conversations. No cloud evaluator was used.

| Scenario | Compact analysis | Compact reply, manually inspected |
|---|---|---|
| `en_hr` | Fenced JSON with `English` rather than lowercase `english`; proposes confirming availability | Speaks as the interviewer and asks the recipient to be available; does not ask for interview details before deciding. |
| `tl_professor` | Fenced JSON with two near-duplicate actions | Does not ask where the paper should be submitted. |
| `mix_hr` | Fenced JSON with `Tagalog` rather than `taglish` | Repeats the sender's availability question; does not ask for interview time and format. |

Compact prompting removed the obvious `Po-po` repetition in these three replies, but all three analysis outputs still fail strict JSON validation and all three replies miss the selected intention. The compact run's 3/3 deterministic reply checks are narrow sentinels, not a quality result. Based on this focused gate and the original 19-case failures, this Gemma 3 1B Q4 artifact is unsuitable for Akma's two-stage MVP prompt workflow as currently configured. A full compact 19-case rerun was intentionally skipped. This desktop finding does not establish Android device performance or rule out other artifacts or prompt designs.
