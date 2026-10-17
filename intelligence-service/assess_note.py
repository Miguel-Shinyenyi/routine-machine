import json
import re
from dataclasses import dataclass

# Instructs the model to answer with nothing but a single JSON object matching this shape, so
# parse_assessment_response has one well-defined thing to parse rather than needing to handle
# open-ended prose. See docs/design.md's Reading page: "chapter placed, own-words percentage,
# ideas connected, quoted phrases marked, one question back."
ASSESSMENT_SYSTEM_PROMPT = """\
You help a reader reflect on notes they take about a book they're reading. Given the book's \
title and the reader's own note, assess the note and respond with nothing but a single JSON \
object, no prose before or after it, matching exactly this shape:

{
  "chapterPlaced": string, your best guess at which chapter or section of the book this note \
is about, based on the note's content,
  "ownWordsPercentage": integer from 0 to 100, how much of the note is the reader's own \
synthesis versus directly quoted or paraphrased-without-insight material from the book,
  "ideasConnected": array of strings, each one a brief observation connecting this note to an \
idea elsewhere in the book or to a prior note, empty array if none,
  "quotedPhrases": array of strings, exact phrases from the note that appear to be direct \
quotations from the book rather than the reader's own words, empty array if none,
  "questionBack": string, one short, specific question that invites the reader to go deeper \
on this note, not a generic prompt
}

Never invent chapter content you were not given. Base the assessment only on the note's own \
text."""


class AssessmentParseError(Exception):
    pass


@dataclass
class ReadingNoteAssessment:
    chapter_placed: str
    own_words_percentage: int
    ideas_connected: list[str]
    quoted_phrases: list[str]
    question_back: str


def build_user_message(note_text: str, book_title: str, current_chapter: int | None, total_chapters: int | None) -> str:
    progress = ""
    if current_chapter is not None and total_chapters is not None:
        progress = f" The reader is on chapter {current_chapter} of {total_chapters}."
    return f'Book: "{book_title}".{progress}\n\nReader\'s note:\n{note_text}'


def parse_assessment_response(raw_text: str) -> ReadingNoteAssessment:
    payload = _extract_json_object(raw_text)

    required = {"chapterPlaced", "ownWordsPercentage", "ideasConnected", "quotedPhrases", "questionBack"}
    missing = required - payload.keys()
    if missing:
        raise AssessmentParseError(f"assessment response is missing fields: {sorted(missing)}")

    percentage = payload["ownWordsPercentage"]
    if not isinstance(percentage, int) or isinstance(percentage, bool) or not (0 <= percentage <= 100):
        raise AssessmentParseError(f"ownWordsPercentage must be an integer 0-100, got {percentage!r}")

    if not isinstance(payload["ideasConnected"], list) or not isinstance(payload["quotedPhrases"], list):
        raise AssessmentParseError("ideasConnected and quotedPhrases must be arrays")

    return ReadingNoteAssessment(
        chapter_placed=str(payload["chapterPlaced"]),
        own_words_percentage=percentage,
        ideas_connected=list(payload["ideasConnected"]),
        quoted_phrases=list(payload["quotedPhrases"]),
        question_back=str(payload["questionBack"]),
    )


def _extract_json_object(raw_text: str) -> dict:
    # The model is instructed to return only JSON, but tolerate a line of prose around it
    # rather than failing outright on an otherwise-good response.
    match = re.search(r"\{.*\}", raw_text, re.DOTALL)
    if not match:
        raise AssessmentParseError("no JSON object found in the assessment response")
    try:
        parsed = json.loads(match.group(0))
    except json.JSONDecodeError as exc:
        raise AssessmentParseError(f"assessment response is not valid JSON: {exc}") from exc
    if not isinstance(parsed, dict):
        raise AssessmentParseError("assessment response's JSON is not an object")
    return parsed
