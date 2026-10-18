import pytest

from analogy import AnalogyParseError, build_user_message, parse_analogy_response


def test_parses_a_well_formed_response():
    raw = '{"analogy": "A goal counts once, however many replays.", "definition": "A key that makes a repeated request produce the same effect as doing it once."}'

    result = parse_analogy_response(raw)

    assert result.analogy == "A goal counts once, however many replays."
    assert result.definition == "A key that makes a repeated request produce the same effect as doing it once."


def test_tolerates_stray_prose_around_the_json():
    raw = 'Sure, here you go:\n{"analogy": "x", "definition": "y"}\nHope that helps!'

    result = parse_analogy_response(raw)

    assert result.analogy == "x"
    assert result.definition == "y"


@pytest.mark.parametrize("missing_field", ["analogy", "definition"])
def test_raises_when_a_required_field_is_missing(missing_field):
    payload = {"analogy": "x", "definition": "y"}
    del payload[missing_field]
    import json
    raw = json.dumps(payload)

    with pytest.raises(AnalogyParseError):
        parse_analogy_response(raw)


def test_raises_when_there_is_no_json_object_at_all():
    with pytest.raises(AnalogyParseError):
        parse_analogy_response("I can't help with that right now.")


def test_raises_on_malformed_json():
    with pytest.raises(AnalogyParseError):
        parse_analogy_response('{"analogy": "x", "definition": }')


def test_build_user_message_names_the_topic_and_interest():
    message = build_user_message("idempotency keys", "football")

    assert "idempotency keys" in message
    assert "football" in message
