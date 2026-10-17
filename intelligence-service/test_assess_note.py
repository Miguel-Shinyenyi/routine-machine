import json

import pytest

from assess_note import AssessmentParseError, parse_assessment_response


def _valid_payload(**overrides):
    payload = {
        "chapterPlaced": "Chapter 3, on habit formation",
        "ownWordsPercentage": 80,
        "ideasConnected": ["Compares it to the author's earlier point about willpower"],
        "quotedPhrases": ["the plateau of latent potential"],
        "questionBack": "What would make this habit automatic for you specifically?",
    }
    payload.update(overrides)
    return payload


def test_parses_a_well_formed_response():
    result = parse_assessment_response(json.dumps(_valid_payload()))

    assert result.chapter_placed == "Chapter 3, on habit formation"
    assert result.own_words_percentage == 80
    assert result.ideas_connected == ["Compares it to the author's earlier point about willpower"]
    assert result.quoted_phrases == ["the plateau of latent potential"]
    assert result.question_back == "What would make this habit automatic for you specifically?"


def test_parses_a_response_with_no_quoted_phrases_or_connected_ideas():
    result = parse_assessment_response(json.dumps(_valid_payload(ideasConnected=[], quotedPhrases=[])))

    assert result.ideas_connected == []
    assert result.quoted_phrases == []


def test_tolerates_surrounding_prose_around_the_json_object():
    raw = "Here is my assessment:\n" + json.dumps(_valid_payload()) + "\nLet me know if that helps."

    result = parse_assessment_response(raw)

    assert result.chapter_placed == "Chapter 3, on habit formation"


@pytest.mark.parametrize("missing_field", [
    "chapterPlaced", "ownWordsPercentage", "ideasConnected", "quotedPhrases", "questionBack",
])
def test_raises_when_a_required_field_is_missing(missing_field):
    payload = _valid_payload()
    del payload[missing_field]

    with pytest.raises(AssessmentParseError):
        parse_assessment_response(json.dumps(payload))


@pytest.mark.parametrize("bad_value", [-1, 101, "80", None])
def test_raises_when_own_words_percentage_is_not_a_valid_0_to_100_integer(bad_value):
    with pytest.raises(AssessmentParseError):
        parse_assessment_response(json.dumps(_valid_payload(ownWordsPercentage=bad_value)))


def test_raises_when_the_response_has_no_json_object_at_all():
    with pytest.raises(AssessmentParseError):
        parse_assessment_response("I couldn't assess this note.")


def test_raises_when_the_json_is_malformed():
    with pytest.raises(AssessmentParseError):
        parse_assessment_response("{chapterPlaced: not valid json}")
