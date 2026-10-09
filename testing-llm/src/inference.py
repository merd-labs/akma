"""Thin adapter over the documented LiteRT-LM Python API."""

from contextlib import contextmanager
from pathlib import Path
from time import perf_counter

import litert_lm

litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)


QWEN_TEMPLATE = (
    Path(__file__).resolve().parents[1] / "prompts/qwen_chat_template.jinja"
).read_text(encoding="utf-8")


@contextmanager
def loaded_model(path):
    started = perf_counter()
    with litert_lm.Engine(str(path), backend=litert_lm.Backend.CPU()) as engine:
        yield engine, perf_counter() - started


def generate(
    engine, system_prompt, user_prompt, max_output_tokens=192, chat_template=None,
):
    started = perf_counter()
    with engine.create_conversation(
        messages=[litert_lm.Message.system(system_prompt)],
        sampler_config=litert_lm.SamplerConfig(temperature=0, seed=42),
        max_output_tokens=max_output_tokens,
        chat_template=chat_template,
    ) as conversation:
        response = conversation.send_message(user_prompt)
    elapsed = perf_counter() - started
    output = "".join(
        item.get("text", "")
        for item in response.get("content", [])
        if item.get("type") == "text"
    )
    return output, elapsed
