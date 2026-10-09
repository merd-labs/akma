"""Build separate system instructions and untrusted user data."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANALYSIS = (ROOT / "prompts/multilingual/analyze.txt").read_text(
    encoding="utf-8"
).split("INCOMING MESSAGE:", 1)[
    0
].strip() + "\nReturn exactly TWO distinct actions with IDs action_1 and action_2. If Filipino and English appear together, including po plus English, detected_language is taglish. Return raw JSON only."
GENERATION = (ROOT / "prompts/multilingual/generate.txt").read_text(
    encoding="utf-8"
).split("INCOMING MESSAGE:", 1)[
    0
].strip() + "\nWrite as the recipient, in first person. You are not an assistant talking to the recipient. Do not say 'I'm here to help'. Never agree, accept, promise, or confirm availability unless the selected intention explicitly asks you to do so. If the incoming message uses po, reply with natural politeness. Return only the message to send."

GEMMA_ANALYSIS = (ROOT / "prompts/gemma_compact/analyze.txt").read_text(
    encoding="utf-8"
).strip()
GEMMA_GENERATION = (ROOT / "prompts/gemma_compact/generate.txt").read_text(
    encoding="utf-8"
).strip()


def instructions(task, profile):
    if profile == "baseline":
        return ANALYSIS if task == "analysis" else GENERATION
    if profile == "gemma_compact":
        return GEMMA_ANALYSIS if task == "analysis" else GEMMA_GENERATION
    raise ValueError(f"Unknown prompt profile: {profile}")


def analyze_prompt(scenario, profile="baseline"):
    data = {
        "incoming_message": scenario["message"],
        "previous_context": scenario.get("context", ""),
    }
    return instructions("analysis", profile), json.dumps(data, ensure_ascii=False)


def generate_prompt(scenario, profile="baseline"):
    data = {
        "incoming_message": scenario["message"],
        "previous_context": scenario.get("context", ""),
        "selected_intention": scenario["intention"],
        "tone": scenario.get("tone", "professional"),
        "language_override": scenario.get("language_override"),
        "user_instructions": scenario.get("user_instructions", ""),
    }
    return instructions("generation", profile), json.dumps(data, ensure_ascii=False)
