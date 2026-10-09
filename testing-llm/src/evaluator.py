"""Deterministic checks and honest report summaries."""

from collections import Counter
import re

from pydantic import ValidationError

from .schemas import parse_analysis


def check_analysis(raw, expected_language, scenario=None):
    try:
        value = parse_analysis(raw)
    except (ValueError, ValidationError) as error:
        return {
            "valid_json_schema": False,
            "language_match": None,
            "forbidden_action_claim_absent": None,
            "error": str(error),
        }
    forbidden = (scenario or {}).get("forbidden_action_claims", [])
    labels = [action.label.casefold() for action in value.suggested_actions]
    return {
        "valid_json_schema": True,
        "language_match": value.detected_language == expected_language,
        "forbidden_action_claim_absent": not any(
            phrase.casefold() in label for phrase in forbidden for label in labels
        ),
        "error": None,
    }


def check_reply(raw, scenario):
    reply = raw.strip()
    normalized = reply.casefold().replace("’", "'").replace("‘", "'")
    intention = scenario.get("intention", "").casefold()
    decision_pending = any(
        phrase in intention
        for phrase in (
            "before deciding", "before confirming", "without committing",
            "avoid confirming", "magpapasya pa lamang",
        )
    )
    commitment = re.search(
        r"\b(?:i\s*(?:am|'m)\s+(?:definitely\s+)?(?:free|available)"
        r"|i\s*(?:will|'ll)\s+(?:be there|join|finish|send)"
        r"|(?:friday|monday)\s+(?:works|is fine))\b",
        normalized,
    )
    checks = {
        "nonempty": bool(reply),
        "reply_only": "```" not in reply
        and not normalized.startswith(("here is", "suggested reply:"))
        and not (reply.startswith('"') and reply.endswith('"')),
        "repetition_absent": not re.search(
            r"\b([a-z]{2,})(?:-\1)+\b|\b([a-z]{2,})(?:\s+\2){2,}\b",
            normalized,
        ),
        "unsupported_commitment_absent": not (decision_pending and commitment),
    }
    forbidden = scenario.get("forbidden_claims", [])
    checks["forbidden_claim_absent"] = not any(
        phrase.casefold().replace("’", "'") in normalized for phrase in forbidden
    )
    return checks


def summarize(cases):
    counts = Counter()
    for case in cases:
        counts["scenarios"] += 1
        counts["analysis_valid"] += bool(
            case.get("analysis", {}).get("checks", {}).get("valid_json_schema")
        )
        counts["language_match"] += bool(
            case.get("analysis", {}).get("checks", {}).get("language_match")
        )
        counts["action_pass"] += bool(
            case.get("analysis", {}).get("checks", {}).get("forbidden_action_claim_absent")
        )
        checks = case.get("generation", {}).get("checks", {})
        counts["reply_pass"] += bool(checks) and all(checks.values())
        counts["repetition_pass"] += bool(checks.get("repetition_absent"))
        counts["commitment_pass"] += bool(checks.get("unsupported_commitment_absent"))
        counts["reply_format_pass"] += bool(checks.get("reply_only"))
        counts["runtime_failures"] += bool(
            case.get("analysis", {}).get("error")
        ) + bool(case.get("generation", {}).get("error"))
        counts["scenario_pass"] += (
            bool(case.get("analysis", {}).get("checks", {}).get("valid_json_schema"))
            and bool(case.get("analysis", {}).get("checks", {}).get("language_match"))
            and bool(case.get("analysis", {}).get("checks", {}).get("forbidden_action_claim_absent"))
            and bool(checks)
            and all(checks.values())
        )
    counts["invalid_json"] = counts["scenarios"] - counts["analysis_valid"]
    return {
        name: counts[name]
        for name in (
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
        )
    }
