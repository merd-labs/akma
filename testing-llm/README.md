# Akma local LLM prompt lab

This Python lab evaluates the exact Qwen2.5 1.5B Q8 and Gemma 3 1B Q4 `.litertlm` files for the Akma offline reply assistant. Earlier prompt files retain the ContextAI working name. No Android code or cloud inference is involved.

## Setup (PowerShell)

```powershell
cd testing-llm
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pip check
```

Gemma requires accepting its license on Hugging Face and authenticating with `hf auth login` or `HF_TOKEN`. Do not put tokens in commands, files, or Git. Downloads use Hugging Face's single-file API, preserve filenames in `models/`, and record verified metadata in `model_manifest.json`.

```powershell
.\.venv\Scripts\python.exe download_models.py --model qwen
.\.venv\Scripts\python.exe download_models.py --model gemma
.\.venv\Scripts\python.exe test_inference.py --model qwen
.\.venv\Scripts\python.exe test_inference.py --model gemma
```

After downloading, turn off network connectivity and repeat the smoke tests to prove local inference. A model is **not** verified merely because download or import succeeds.

The Qwen artifact's embedded chat template fails with `litert-lm-api==0.18.0` because it treats the API's content sequence as a string. The lab uses the documented `chat_template` override in `prompts/qwen_chat_template.jinja` with Qwen's chat delimiters; this workaround must be recorded when comparing runs. Gemma keeps its embedded template unless testing shows a similar incompatibility.

## Benchmark

```powershell
.\.venv\Scripts\python.exe benchmark.py --model qwen --scenario mix_hr
.\.venv\Scripts\python.exe benchmark.py --model all
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

For a focused Gemma prompt comparison, use the same three scenario IDs with separate output folders. The default `baseline` profile keeps the original multilingual prompts; `gemma_compact` removes repeated Filipino framing while keeping the scenario data and inference settings identical. Repeat `--scenario` to select more than one case.

```powershell
.\.venv\Scripts\python.exe benchmark.py --model gemma --scenario en_hr --scenario tl_professor --scenario mix_hr --output-dir results/gemma_probe_baseline
.\.venv\Scripts\python.exe benchmark.py --model gemma --prompt-profile gemma_compact --scenario en_hr --scenario tl_professor --scenario mix_hr --output-dir results/gemma_probe_compact
```

The reply checks separately flag repeated words, obvious formatting, and selected-intention conflicts involving availability or commitments. These are narrow sentinels; inspect every raw reply for faithfulness. Running `--model all` benchmarks Qwen2.5 and Gemma sequentially. The original 19-case Gemma run is preserved in `results/gemma_results.json`.

Each task runs in a fresh conversation. Both models use CPU, temperature 0, seed 42, 192 analysis output tokens and 128 reply output tokens. The seed does not guarantee identical outputs across models. The synchronous API provides combined prompt processing plus generation time; its parts are not separately measured. Model load time is separate. No reliable peak memory value is claimed by this lab.

Results include raw outputs and errors. `human_review.csv` anonymizes model names per scenario. Keep `review_key.json` from reviewers until scoring finishes. Do not rerun the benchmark after filling the review sheet without saving the completed sheet first.

After reviewers fill every score field for each reviewed row, run `.\.venv\Scripts\python.exe review_report.py` to append only completed human ratings to `benchmark_report.md`.

## Human scoring rubric

Score each output from **1 (unusable)** to **5 (excellent)**. Leave a field blank if the output is missing or the criterion cannot be judged. Review the incoming message, selected intention, analysis output, and reply together.

| Field | 1 | 3 | 5 |
|---|---|---|---|
| Intent understanding | Misreads the request | Gets the main purpose with omissions | Captures purpose and ambiguity |
| Action relevance | Irrelevant or unsafe choices | Some useful choices | Distinct, appropriate choices |
| Tone | Inappropriate | Acceptable with awkwardness | Fits relationship and situation |
| Language quality | Unnatural or wrong language | Understandable | Natural English, Tagalog, or Taglish |
| Intention faithfulness | Contradicts chosen intention | Partly follows | Fully follows without extra commitments |
| Fabrication | Invents consequential facts | Minor unsupported wording | No invented facts or promises |

The deterministic checks cover JSON shape, language label, nonempty reply, obvious formatting, and scenario-specific forbidden phrases in replies and action labels. These are known-wording sentinels, not proof of factual faithfulness or natural language quality. A reviewer should note honorific fit, code-switching, and prompt-injection behavior.

## Decision gate

Compare reviewed quality, JSON validity, failures, file size, and latency. Qwen Q8 versus Gemma Q4 is not a controlled quantization comparison. Desktop timing cannot establish Android performance. The Android 11 Pova 2 needs its own offline load, latency, memory, and reply-faithfulness checks before model lock-in.
