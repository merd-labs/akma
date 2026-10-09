"""Run a real, local CPU smoke test for the requested model file."""

import argparse
import sys
from pathlib import Path

from download_models import CANDIDATES
from src.inference import QWEN_TEMPLATE, generate, loaded_model

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=[*CANDIDATES, "all"], required=True)
    args = parser.parse_args()
    failed = False
    for name in CANDIDATES if args.model == "all" else [args.model]:
        path = ROOT / "models" / CANDIDATES[name][1]
        if not path.is_file():
            print(f"{name}: model file missing: {path}")
            failed = True
            continue
        try:
            with loaded_model(path) as (engine, load_seconds):
                output, generation_seconds = generate(
                    engine,
                    "Identify the sender's request in one short sentence. Do not draft a reply or assume the recipient's availability.",
                    "What is the sender asking in this message? Hi po! Available po ba kayo tomorrow for an interview?",
                    max_output_tokens=80,
                    chat_template=QWEN_TEMPLATE if name == "qwen" else None,
                )
            print(
                f"{name}: load={load_seconds:.2f}s generation={generation_seconds:.2f}s output={output!r}"
            )
            if not output.strip():
                failed = True
        except (RuntimeError, MemoryError, OSError, ValueError) as error:
            print(f"{name}: {type(error).__name__}: {error}")
            failed = True
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
