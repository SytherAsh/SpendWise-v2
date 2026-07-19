from training.merchant_normalizer import (
    basic_normalize,
    canonicalize,
    canonicalize_with_ambiguities,
    cluster_by_fuzzy_name,
    find_ambiguous_fuzzy_pairs,
    find_prefix_ambiguities,
    find_prefix_variants,
    merge_prefix_chains,
)


def test_basic_normalize_uppercases_trims_and_collapses_whitespace() -> None:
    assert basic_normalize("  swiggy   bangalore  ") == "SWIGGY BANGALORE"
    assert basic_normalize("SNOW_CRE") == "SNOW CRE"


def test_basic_normalize_handles_null_and_sentinels() -> None:
    assert basic_normalize(None) == "UNKNOWN"
    assert basic_normalize("PHONE_TRANSFER") == "PHONE_TRANSFER"


def test_basic_normalize_strips_leading_honorific() -> None:
    assert basic_normalize("Mr Yash") == "YASH"
    assert basic_normalize("Mrs. Prachi Sameer Sawant") == "PRACHI SAMEER SAWANT"
    assert basic_normalize("dr sameer") == "SAMEER"


def test_basic_normalize_keeps_title_only_name_intact() -> None:
    """A name that is nothing but a title (no actual name left after
    stripping) must not be reduced to an empty string."""
    assert basic_normalize("Dr") == "DR"


def test_cluster_by_fuzzy_name_merges_near_duplicates() -> None:
    names = ["SWIGGY BANGALORE", "SWIGGY BANGALOR", "SWIGGY BANGALORE"]

    clusters = cluster_by_fuzzy_name(names, threshold=90)

    assert len(set(clusters.values())) == 1
    assert clusters["SWIGGY BANGALORE"] == "SWIGGY BANGALORE"  # most frequent variant wins


def test_cluster_by_fuzzy_name_keeps_dissimilar_names_apart() -> None:
    names = ["SWIGGY BANGALORE", "UBER INDIA"]

    clusters = cluster_by_fuzzy_name(names, threshold=90)

    assert clusters["SWIGGY BANGALORE"] != clusters["UBER INDIA"]


def test_first_name_conflict_prevents_merge_despite_shared_surname() -> None:
    names = ["PRACHI SAMEER SAWANT", "YASH SAMEER SAWANT"]

    clusters = cluster_by_fuzzy_name(names, threshold=50)

    assert clusters["PRACHI SAMEER SAWANT"] != clusters["YASH SAMEER SAWANT"]


def test_find_prefix_variants_matches_truncated_names() -> None:
    pairs = find_prefix_variants(["SAMEER SAWANT", "SAMEER SAWA"], min_len=6)

    assert ("SAMEER SAWA", "SAMEER SAWANT") in pairs


def test_find_prefix_variants_respects_min_len() -> None:
    pairs = find_prefix_variants(["AB", "ABCDEF"], min_len=6)

    assert pairs == []


def test_merge_prefix_chains_merges_single_truncation_variant() -> None:
    mapping = merge_prefix_chains(["SAMEER SAWANT", "SAMEER SAWA"], min_len=6)

    assert mapping["SAMEER SAWA"] == "SAMEER SAWANT"
    assert mapping["SAMEER SAWANT"] == "SAMEER SAWANT"


def test_merge_prefix_chains_leaves_ambiguous_shared_prefix_unmerged() -> None:
    """Two different people sharing a short common prefix must not be
    arbitrarily attributed to whichever has the longer string."""
    mapping = merge_prefix_chains(
        ["MAHENDRA", "MAHENDRA DUNGARSHI", "MAHENDRA KAILAS KOLI"], min_len=6
    )

    assert mapping["MAHENDRA"] == "MAHENDRA"
    assert mapping["MAHENDRA DUNGARSHI"] == "MAHENDRA DUNGARSHI"
    assert mapping["MAHENDRA KAILAS KOLI"] == "MAHENDRA KAILAS KOLI"


def test_canonicalize_groups_by_upi_id_over_name_spelling() -> None:
    entries = [
        {"key": "t1", "recipient_name": "Netflix", "upi_id": "netflix@okicici"},
        {"key": "t2", "recipient_name": "NETFLIX INDIA", "upi_id": "netflix@okicici"},
        {"key": "t3", "recipient_name": "Uber", "upi_id": "uber@okhdfc"},
    ]

    result = canonicalize(entries)

    assert result["t1"] == result["t2"]
    assert result["t1"] != result["t3"]


def test_canonicalize_returns_empty_for_no_entries() -> None:
    assert canonicalize([]) == {}


def test_canonicalize_handles_missing_recipient_and_upi_gracefully() -> None:
    entries = [{"key": "t1", "recipient_name": None, "upi_id": None}]

    result = canonicalize(entries)

    assert result["t1"] == "UNKNOWN"


def test_find_ambiguous_fuzzy_pairs_surfaces_near_miss() -> None:
    """AIRTEL PREPAID / AIRTEL POSTPAID score ~83 (rapidfuzz token_sort_ratio) --
    below the 90 auto-merge threshold, above a 78 review floor, and land in
    different clusters -- exactly the "maybe the same, ask the user" case."""
    names = ["AIRTEL PREPAID", "AIRTEL POSTPAID"]
    clusters = cluster_by_fuzzy_name(names, threshold=90)
    assert clusters["AIRTEL PREPAID"] != clusters["AIRTEL POSTPAID"]  # not auto-merged

    ambiguous = find_ambiguous_fuzzy_pairs(names, clusters, threshold=90, review_floor=78)

    assert len(ambiguous) == 1
    assert {ambiguous[0]["name_a"], ambiguous[0]["name_b"]} == {"AIRTEL PREPAID", "AIRTEL POSTPAID"}
    assert 78 <= ambiguous[0]["score"] < 90


def test_find_ambiguous_fuzzy_pairs_excludes_first_name_conflicts() -> None:
    """PRACHI SAMEER SAWANT / YASH SAMEER SAWANT share 2 of 3 tokens (a high raw
    token_sort_ratio) but are forced to score 0 by the first-name-conflict guard
    -- a confident "different person" verdict, never an ambiguous review
    candidate, no matter how similar the rest of the string is."""
    names = ["PRACHI SAMEER SAWANT", "YASH SAMEER SAWANT"]
    clusters = cluster_by_fuzzy_name(names, threshold=50)

    ambiguous = find_ambiguous_fuzzy_pairs(names, clusters, threshold=90, review_floor=10)

    assert ambiguous == []


def test_find_ambiguous_fuzzy_pairs_excludes_already_merged_pairs() -> None:
    """A pair that already clusters together at `threshold` is a solved case,
    not an open question."""
    names = ["SWIGGY BANGALORE", "SWIGGY BANGALOR"]
    clusters = cluster_by_fuzzy_name(names, threshold=90)
    assert clusters["SWIGGY BANGALORE"] == clusters["SWIGGY BANGALOR"]

    ambiguous = find_ambiguous_fuzzy_pairs(names, clusters, threshold=90, review_floor=50)

    assert ambiguous == []


def test_find_prefix_ambiguities_surfaces_multi_target_case() -> None:
    """Mirrors test_merge_prefix_chains_leaves_ambiguous_shared_prefix_unmerged --
    the same component, but asking for *why* it's ambiguous instead of just that
    it stayed unmerged."""
    ambiguities = find_prefix_ambiguities(
        ["MAHENDRA", "MAHENDRA DUNGARSHI", "MAHENDRA KAILAS KOLI"], min_len=6
    )

    assert len(ambiguities) == 1
    assert ambiguities[0]["name"] == "MAHENDRA"
    assert set(ambiguities[0]["targets"]) == {"MAHENDRA DUNGARSHI", "MAHENDRA KAILAS KOLI"}


def test_find_prefix_ambiguities_excludes_clean_single_target_merges() -> None:
    ambiguities = find_prefix_ambiguities(["SAMEER SAWANT", "SAMEER SAWA"], min_len=6)

    assert ambiguities == []


def test_canonicalize_with_ambiguities_matches_canonicalize_exactly() -> None:
    """canonicalize() is defined in terms of canonicalize_with_ambiguities() --
    they must never disagree on canonical_names."""
    entries = [
        {"key": "t1", "recipient_name": "Netflix", "upi_id": "netflix@okicici"},
        {"key": "t2", "recipient_name": "NETFLIX INDIA", "upi_id": "netflix@okicici"},
        {"key": "t3", "recipient_name": "Uber", "upi_id": "uber@okhdfc"},
    ]

    assert canonicalize_with_ambiguities(entries)["canonical_names"] == canonicalize(entries)


def test_canonicalize_with_ambiguities_surfaces_real_sameer_case() -> None:
    """The exact pattern found in real account data this feature was built for:
    a bare first name ambiguous against two fuller, distinctly-different-looking
    canonical names."""
    entries = [
        {"key": "a1", "recipient_name": "Sameer", "upi_id": None},
        {"key": "a2", "recipient_name": "SAMEER SAWANT", "upi_id": None},
        {"key": "a3", "recipient_name": "SAMEER BALIRAM SAWA", "upi_id": None},
        {"key": "a4", "recipient_name": "Yash Sameer Sawant", "upi_id": None},
    ]

    result = canonicalize_with_ambiguities(entries)

    assert result["canonical_names"]["a2"] != result["canonical_names"]["a3"]  # still not auto-merged
    anchors = {g["anchor_key"] for g in result["ambiguous_groups"]}
    assert anchors == {"a2", "a3"}
    for group in result["ambiguous_groups"]:
        assert [c["key"] for c in group["candidates"]] == ["a1"]
    # YASH SAMEER SAWANT never appears -- first-name-conflict already resolved it confidently.
    all_candidate_keys = {c["key"] for g in result["ambiguous_groups"] for c in g["candidates"]}
    assert "a4" not in all_candidate_keys


def test_canonicalize_with_ambiguities_empty_entries() -> None:
    assert canonicalize_with_ambiguities([]) == {"canonical_names": {}, "ambiguous_groups": []}
