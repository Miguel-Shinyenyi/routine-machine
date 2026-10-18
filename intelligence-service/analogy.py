import json
import re
from dataclasses import dataclass

# Phase 10: explains a learning topic through one of the user's own interests, always
# alongside the precise definition — see docs/product-direction.md's Profile section
# ("the analogy comes first, then the precise definition... labelled as an analogy, never as
# a source of truth"). Same JSON-only-response pattern assess_note.py already uses.
ANALOGY_SYSTEM_PROMPT = """\
You explain a technical topic to someone by connecting it to one thing they love. Given a \
topic and one interest, respond with nothing but a single JSON object, no prose before or \
after it, matching exactly this shape:

{
  "analogy": string, a short, vivid explanation of the topic using the given interest as the \
comparison. Specific to the interest, not generic.,
  "definition": string, the precise, accurate technical definition of the topic, with no \
analogy or metaphor in it
}

Never claim the analogy is itself the technical truth; it is an aid, and the definition field \
is what must be accurate."""


class AnalogyParseError(Exception):
    pass


@dataclass
class Analogy:
    analogy: str
    definition: str


def build_user_message(topic: str, interest: str) -> str:
    return f'Topic: "{topic}"\nExplain it through: {interest}'


def parse_analogy_response(raw_text: str) -> Analogy:
    payload = _extract_json_object(raw_text)

    required = {"analogy", "definition"}
    missing = required - payload.keys()
    if missing:
        raise AnalogyParseError(f"analogy response is missing fields: {sorted(missing)}")

    return Analogy(analogy=str(payload["analogy"]), definition=str(payload["definition"]))


def _extract_json_object(raw_text: str) -> dict:
    match = re.search(r"\{.*\}", raw_text, re.DOTALL)
    if not match:
        raise AnalogyParseError("no JSON object found in the analogy response")
    try:
        parsed = json.loads(match.group(0))
    except json.JSONDecodeError as exc:
        raise AnalogyParseError(f"analogy response is not valid JSON: {exc}") from exc
    if not isinstance(parsed, dict):
        raise AnalogyParseError("analogy response's JSON is not an object")
    return parsed
