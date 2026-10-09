"""Benchmark exact LiteRT-LM files on the shared synthetic dataset."""

import argparse
import csv
import json
import random
from datetime import datetime, timezone
from pathlib import Path

from download_models import CANDIDATES
from src.evaluator import check_analysis, check_reply, summarize
from src.inference import QWEN_TEMPLATE, generate, loaded_model
from src.prompt_builder import analyze_prompt, generate_prompt

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "results"
DATASET = ROOT / "datasets/scenarios.json"


def write_json(path, data):
    path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def run_task(engine, prompt, limit, chat_template=None):
    try:
        output, seconds = generate(
            engine, *prompt, max_output_tokens=limit, chat_template=chat_template,
        )
        return {
            "output": output,
            "duration_seconds": round(seconds, 3),
            "output_chars": len(output),
            "error": None,
        }
    except (RuntimeError, MemoryError, OSError, ValueError) as error:
        return {
            "output": "",
            "duration_seconds": None,
            "output_chars": 0,
            "error": f"{type(error).__name__}: {error}",
        }


def benchmark_model(name, scenarios, profile="baseline", output_dir=RESULTS):
    path = ROOT / "models" / CANDIDATES[name][1]
    result = {
        "model": name,
        "prompt_profile": profile,
        "repository": CANDIDATES[name][0],
        "artifact": CANDIDATES[name][1],
        "runtime": "litert-lm-api 0.18.0",
        "backend": "CPU",
        "temperature": 0,
        "seed": 42,
        "analysis_max_output_tokens": 192,
        "generation_max_output_tokens": 128,
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "model_size_bytes": path.stat().st_size if path.exists() else None,
        "load_seconds": None,
        "load_error": None,
        "peak_memory_bytes": None,
        "memory_note": "Peak process memory was not measured reliably on this run.",
        "cases": [],
    }
    if not path.is_file():
        result["load_error"] = "Model file missing; run download_models.py first"
    else:
        try:
            with loaded_model(path) as (engine, load_seconds):
                result["load_seconds"] = round(load_seconds, 3)
                for scenario in scenarios:
                    case = {"id": scenario["id"], "language": scenario["language"]}
                    template = QWEN_TEMPLATE if name == "qwen" else None
                    analysis = run_task(engine, analyze_prompt(scenario, profile), 192, template)
                    analysis["checks"] = check_analysis(
                        analysis["output"], scenario["language"], scenario
                    )
                    case["analysis"] = analysis
                    generation = run_task(
                        engine, generate_prompt(scenario, profile), 128, template
                    )
                    generation["checks"] = check_reply(generation["output"], scenario)
                    case["generation"] = generation
                    result["cases"].append(case)
                    print(
                        f"{name}/{scenario['id']}: analysis={'ok' if analysis['checks']['valid_json_schema'] else 'invalid'} reply={'ok' if all(generation['checks'].values()) else 'check'}",
                        flush=True,
                    )
        except (RuntimeError, MemoryError, OSError, ValueError) as error:
            result["load_error"] = f"{type(error).__name__}: {error}"
    result["summary"] = summarize(result["cases"])
    write_json(output_dir / f"{name}_results.json", result)
    return result


def make_reports(all_results, scenarios, output_dir=RESULTS):
    with (output_dir / "comparison.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as file:
        writer = csv.DictWriter(
            file,
            fieldnames=[
                "model",
                "loaded",
                "model_size_bytes",
                "load_seconds",
                "scenarios",
                "analysis_valid",
                "invalid_json",
                "language_match",
                "action_pass",
                "reply_pass",
                "repetition_pass",
                "commitment_pass",
                "reply_format_pass",
                "scenario_pass",
                "runtime_failures",
                "analysis_mean_seconds",
                "generation_mean_seconds",
            ],
        )
        writer.writeheader()
        for result in all_results:
            cases = result["cases"]

            def mean(task):
                samples = [
                    case[task]["duration_seconds"]
                    for case in cases
                    if case[task]["duration_seconds"] is not None
                ]
                return round(sum(samples) / len(samples), 3) if samples else ""

            writer.writerow(
                {
                    "model": result["model"],
                    "loaded": result["load_seconds"] is not None,
                    "model_size_bytes": result["model_size_bytes"],
                    "load_seconds": result["load_seconds"],
                    **result["summary"],
                    "analysis_mean_seconds": mean("analysis"),
                    "generation_mean_seconds": mean("generation"),
                }
            )
    by_id = {
        result["model"]: {case["id"]: case for case in result["cases"]}
        for result in all_results
    }
    review_rows = []
    review_key = {}
    rng = random.Random(20261009)
    for scenario in scenarios:
        entries = [
            (model, cases[scenario["id"]])
            for model, cases in by_id.items()
            if scenario["id"] in cases
        ]
        rng.shuffle(entries)
        for index, (model, case) in enumerate(entries, start=1):
            blind_id = f"{scenario['id']}-{index}"
            review_key[blind_id] = model
            review_rows.append(
                {
                    "blind_id": blind_id,
                    "scenario_id": scenario["id"],
                    "language": scenario["language"],
                    "incoming_message": scenario["message"],
                    "selected_intention": scenario["intention"],
                    "analysis_output": case["analysis"]["output"],
                    "reply_output": case["generation"]["output"],
                    "intent_understanding_1to5": "",
                    "action_relevance_1to5": "",
                    "tone_1to5": "",
                    "language_quality_1to5": "",
                    "intention_faithfulness_1to5": "",
                    "fabrication_1to5": "",
                    "reviewer_notes": "",
                }
            )
    with (output_dir / "human_review.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as file:
        writer = csv.DictWriter(
            file,
            fieldnames=(
                list(review_rows[0])
                if review_rows
                else [
                    "blind_id",
                    "scenario_id",
                    "language",
                    "incoming_message",
                    "selected_intention",
                    "analysis_output",
                    "reply_output",
                    "intent_understanding_1to5",
                    "action_relevance_1to5",
                    "tone_1to5",
                    "language_quality_1to5",
                    "intention_faithfulness_1to5",
                    "fabrication_1to5",
                    "reviewer_notes",
                ]
            ),
        )
        writer.writeheader()
        writer.writerows(review_rows)
    write_json(output_dir / "review_key.json", review_key)
    lines = [
        "# Akma LiteRT-LM benchmark",
        "",
        "Desktop Windows CPU results; not proof of Android performance.",
        f"Prompt profile: {all_results[0]['prompt_profile']}. Deterministic checks are limited sentinels, not quality scores.",
        "",
        "## Technical status",
        "",
    ]
    for result in all_results:
        status = (
            f"loaded in {result['load_seconds']} s"
            if result["load_seconds"] is not None
            else f"not loaded: {result['load_error']}"
        )
        summary = result["summary"]
        lines.append(
            f"- **{result['model']}**: {status}; file size {result['model_size_bytes']} bytes; {summary['scenarios']} scenarios, {summary['analysis_valid']} valid analysis JSON, {summary['invalid_json']} invalid, {summary['language_match']} correct language labels, {summary['action_pass']} action-label sentinel checks passed, {summary['reply_pass']} combined reply checks passed, {summary['repetition_pass']} repetition checks passed, {summary['commitment_pass']} commitment checks passed, {summary['reply_format_pass']} format checks passed, {summary['runtime_failures']} runtime failures."
        )
        if result.get("evaluation_note"):
            lines.append(f"  - {result['evaluation_note']}")
    lines += [
        "",
        "Detailed response times and output lengths are in the result JSON files; means are in `comparison.csv`. Peak memory was not measured reliably.",
        "",
        "## Quality review",
        "",
        "Human scores are pending. Strengths and weaknesses in intent understanding, action relevance, tone, English, Tagalog, and Taglish quality cannot yet be rated.",
        "",
        "The reviewer sheet contains anonymous outputs and empty 1–5 scores. Keep `review_key.json` away from reviewers until scoring is complete.",
        "",
        "## Provisional recommendation",
        "",
        "None while comparative human review or device proof is missing.",
        "",
        "## Interpretation",
        "",
        "Qwen uses Q8 and Gemma uses Q4, so model architecture and quantization both affect size and quality. Synchronous API timing combines prompt processing and generation. The Android 11 Pova 2 still needs offline load, latency, memory, and reply-faithfulness validation.",
        "",
    ]
    lines += ["", "## Measured strengths and limitations", ""]
    for result in all_results:
        cases = result["cases"]
        if cases:
            wrong = [
                case
                for case in cases
                if not case["analysis"]["checks"]["language_match"]
            ]
            by_language = {
                language: sum(case["language"] == language for case in wrong)
                for language in ("english", "tagalog", "taglish")
            }
            lines.append(
                f"- **{result['model']}**: {result['summary']['analysis_valid']}/{len(cases)} valid JSON; language-label errors {by_language}; {len(cases) - result['summary']['action_pass']} action-label sentinel failures; {len(cases) - result['summary']['reply_pass']} deterministic reply check failures. Human language and faithfulness scores are pending."
            )
    lines += [
        "",
        "Mean task latencies are in `comparison.csv`; per-scenario times and output lengths are in the model JSON files. Qwen uses a chat-template override because the artifact's embedded template is incompatible with the installed Python API.",
        "",
    ]
    (output_dir / "benchmark_report.md").write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=[*CANDIDATES, "all"], required=True)
    parser.add_argument("--scenario", action="append", help="Run one scenario ID; repeat to select several")
    parser.add_argument("--prompt-profile", choices=("baseline", "gemma_compact"), default="baseline")
    parser.add_argument("--output-dir", type=Path, default=RESULTS)
    args = parser.parse_args()
    scenarios = json.loads(DATASET.read_text(encoding="utf-8"))
    if args.scenario:
        unknown = set(args.scenario) - {case["id"] for case in scenarios}
        if unknown:
            parser.error(f"Unknown scenario ID: {', '.join(sorted(unknown))}")
        scenarios = [case for case in scenarios if case["id"] in set(args.scenario)]
    if args.prompt_profile == "gemma_compact" and args.model != "gemma":
        parser.error("gemma_compact profile is only for --model gemma")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    names = list(CANDIDATES) if args.model == "all" else [args.model]
    results = [benchmark_model(name, scenarios, args.prompt_profile, args.output_dir) for name in names]
    make_reports(results, scenarios, args.output_dir)


if __name__ == "__main__":
    main()
