from training.merchant_normalizer import (
    basic_normalize,
    canonicalize,
    cluster_by_fuzzy_name,
    find_prefix_variants,
    merge_prefix_chains,
)


def test_basic_normalize_uppercases_trims_and_collapses_whitespace() -> None:
    assert basic_normalize("  swiggy   bangalore  ") == "SWIGGY BANGALORE"
    assert basic_normalize("SNOW_CRE") == "SNOW CRE"


def test_basic_normalize_handles_null_and_sentinels() -> None:
    assert basic_normalize(None) == "UNKNOWN"
    assert basic_normalize("PHONE_TRANSFER") == "PHONE_TRANSFER"


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
