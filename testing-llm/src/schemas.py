"""Strict structure checks; quality still requires human judgment."""

import json
from typing import Literal

from pydantic import BaseModel, Field, ConfigDict, model_validator


class SuggestedAction(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1)
    label: str = Field(min_length=1)


class Analysis(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message_purpose: str = Field(min_length=1)
    detected_language: Literal["english", "tagalog", "taglish"]
    recommended_tone: str = Field(min_length=1)
    suggested_actions: list[SuggestedAction] = Field(min_length=2, max_length=3)

    @model_validator(mode="after")
    def unique_action_ids(self):
        ids = [action.id for action in self.suggested_actions]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate action IDs")
        return self


def parse_analysis(raw):
    return Analysis.model_validate(json.loads(raw))
