from docvoice.segment import (
    Segment, Sentence, assign_speakers, build_sentences, english_word_count, group_paragraphs,
    merge_short_english, split_sentences,
)


def test_split_basic_korean_english():
    s = split_sentences("안녕하세요. 오늘은 날씨가 좋습니다! Is it true? Yes it is.")
    assert s == ["안녕하세요.", "오늘은 날씨가 좋습니다!", "Is it true?", "Yes it is."]


def test_split_keeps_abbreviations_decimals_initials():
    s = split_sentences("Dr. Smith paid 3.5 dollars. J. Kim agreed, e.g. at noon. Then we left.")
    assert s == ["Dr. Smith paid 3.5 dollars.", "J. Kim agreed, e.g. at noon.", "Then we left."]


def test_split_list_numbers_and_newlines():
    s = split_sentences("1. 개요\n2. 본문 내용입니다. 끝")
    assert s == ["1. 개요", "2. 본문 내용입니다.", "끝"]


def test_english_word_count():
    assert english_word_count("Yes.") == 1
    assert english_word_count("I don't know.") == 3
    assert english_word_count("well-known fact") == 2


def test_merge_short_english_into_previous():
    sents = [Sentence(0, 2, "This is a full sentence."), Sentence(2, 3, "Yes."),
             Sentence(3, 5, "Okay then, let us go.")]
    out = merge_short_english(sents, 3)
    assert [s.text for s in out] == ["This is a full sentence. Yes.", "Okay then, let us go."]
    assert out[0].end == 3


def test_merge_short_english_leading_goes_to_next():
    sents = [Sentence(0, 1, "Hi."), Sentence(1, 4, "Welcome to the show.")]
    out = merge_short_english(sents, 3)
    assert [s.text for s in out] == ["Hi. Welcome to the show."]
    assert out[0].start == 0


def test_korean_short_sentence_not_merged():
    sents = [Sentence(0, 2, "오늘 날씨가 좋습니다."), Sentence(2, 3, "네.")]
    assert len(merge_short_english(sents, 3)) == 2


def test_build_sentences_across_segments_and_timing():
    segs = [Segment(0.0, 4.0, "Hello everyone, welcome to"), Segment(4.0, 8.0, "the show. Today we talk about AI.")]
    out = build_sentences(segs)
    assert [s.text for s in out] == ["Hello everyone, welcome to the show.", "Today we talk about AI."]
    assert out[0].start == 0.0
    assert 4.0 < out[0].end < 6.0 and out[1].end == 8.0


def test_build_sentences_no_punctuation_uses_pause_and_korean_ending():
    segs = [Segment(0, 3, "안녕하세요 반갑습니다"), Segment(3.1, 6, "오늘은 회의를 시작하겠습니다"),
            Segment(9, 12, "다음 안건입니다")]
    out = build_sentences(segs)
    assert [s.text for s in out] == ["안녕하세요 반갑습니다", "오늘은 회의를 시작하겠습니다", "다음 안건입니다"]


def test_build_sentences_drops_symbol_only():
    out = build_sentences([Segment(0, 1, "..."), Segment(1, 3, "This is real text.")])
    assert [s.text for s in out] == ["This is real text."]


def test_assign_speakers_and_paragraph_grouping():
    sents = [Sentence(0, 2, "A one."), Sentence(2.2, 4, "A two."), Sentence(4.1, 6, "B one."),
             Sentence(6.2, 8, "B two."), Sentence(12, 14, "B after a long pause.")]
    assign_speakers(sents, [(0, 4.05, 0), (4.05, 14, 1)])
    assert [s.speaker for s in sents] == [0, 0, 1, 1, 1]
    paras = group_paragraphs(sents, gap=1.5)
    assert [p.text for p in paras] == ["A one. A two.", "B one. B two.", "B after a long pause."]


def test_group_paragraphs_gap_only_without_speakers():
    sents = [Sentence(0, 2, "One."), Sentence(2.5, 4, "Two."), Sentence(8, 9, "Three.")]
    assert len(group_paragraphs(sents, gap=1.5)) == 2
    assert len(group_paragraphs(sents, gap=5.0)) == 1


def test_group_paragraphs_length_cap():
    sents = [Sentence(i, i + 0.9, "x" * 300) for i in range(5)]
    paras = group_paragraphs(sents, gap=10, max_chars=500)
    assert len(paras) > 1
