"""Append actual completed human-review scores to the benchmark report."""

import csv
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "results"
FIELDS = [
    "intent_understanding_1to5",
    "action_relevance_1to5",
    "tone_1to5",
    "language_quality_1to5",
    "intention_faithfulness_1to5",
    "fabrication_1to5",
]
MARKER = "## Completed human review"


def main():
    key = json.loads((RESULTS / "review_key.json").read_text(encoding="utf-8"))
    with (RESULTS / "human_review.csv").open(newline="", encoding="utf-8-sig") as file:
        rows = list(csv.DictReader(file))
    scores = defaultdict(lambda: defaultdict(list))
    language_scores = defaultdict(lambda: defaultdict(list))
    completed = defaultdict(int)
    for row in rows:
        values = [row[field].strip() for field in FIELDS]
        if not any(values):
            continue
        if not all(values):
            raise ValueError(f"Incomplete reviewer scores for {row['blind_id']}")
        if any(value not in ("1", "2", "3", "4", "5") for value in values):
            raise ValueError(f"Scores must be integers 1–5 for {row['blind_id']}")
        model = key[row["blind_id"]]
        completed[model] += 1
        for field, value in zip(FIELDS, values):
            scores[model][field].append(int(value))
        language_scores[model][row["language"]].append(
            int(row["language_quality_1to5"])
        )
    report = RESULTS / "benchmark_report.md"
    original = report.read_text(encoding="utf-8").split(MARKER, 1)[0].rstrip()
    lines = [original, "", MARKER, ""]
    if not completed:
        lines.append(
            "No completed reviewer rows yet; language quality remains unscored."
        )
    for model in sorted(completed):
        lines.append(f"### {model} — {completed[model]} reviewed scenarios")
        lines.append("")
        for field in FIELDS:
            values = scores[model][field]
            lines.append(f"- {field}: {sum(values)/len(values):.2f}/5")
        for language in ("english", "tagalog", "taglish"):
            values = language_scores[model][language]
            if values:
                lines.append(
                    f"- {language} quality: {sum(values)/len(values):.2f}/5 ({len(values)} reviews)"
                )
        lines.append("")
    lines.append(
        "These are human ratings, not automatic accuracy claims. Review notes and Android device results remain necessary for a model recommendation."
    )
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote human review from {sum(completed.values())} completed rows")


if __name__ == "__main__":
    main()
