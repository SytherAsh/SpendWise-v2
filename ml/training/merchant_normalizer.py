"""Bank-agnostic merchant/recipient name normalization.

Two-tier canonicalization for a `Recipient_Name` column produced by any
bank-specific statement parser:

1. Exact `UPI_ID` grouping (primary) — rows sharing a UPI ID are
   unambiguously the same payee, regardless of how the name was spelled
   on a given transaction.
2. Fuzzy name clustering (fallback) — for rows without a usable UPI ID,
   cluster near-duplicate name spellings with rapidfuzz similarity +
   scipy hierarchical (complete-linkage) clustering.

Ported near-verbatim (ML strategy phase, 2026-07-13) from the offline
labeling pipeline that produced ml/data/spendwise_labeled.xlsx's
Recipient_Canonical column — same thresholds, same guards, so canonical
names computed here match what already shipped in the training data.
"""

import itertools
import re
from collections import Counter

import numpy as np
import pandas as pd
from rapidfuzz import fuzz
from scipy.cluster.hierarchy import fcluster, linkage
from scipy.spatial.distance import squareform

SENTINELS = {
    "UNKNOWN",
    "PHONE_TRANSFER",
    "ATM_WITHDRAWAL",
    "BANK_FEE",
    "INTEREST_CREDIT",
    "INSURANCE_PREMIUM",
    "CHEQUE_CLEARING",
    "REVERSAL",
}
_WHITESPACE_RE = re.compile(r"\s+")
_SEPARATOR_RE = re.compile(r"[_]+")
_TITLE_TOKENS = {"MR", "MRS", "MS", "MISS", "DR"}


def _strip_titles(name: str) -> str:
    """Drop leading honorific tokens (Mr/Mrs/Ms/Miss/Dr) so they don't become
    part of the stored canonical name.

    Without this, "Mr Yash" and a bare "Yash" elsewhere in the same user's
    history fuzzy-compare as two extra characters apart on token_sort_ratio —
    enough on short names to land under the 90 threshold and never merge, and
    even when they do merge the honorific can win as the "canonical" display
    form since it's just as frequent as the bare name. Stripping it here means
    the title never enters the similarity comparison or the stored name at
    all, rather than only being skipped ad hoc inside
    `_first_names_conflict`'s first-token check.

    Only strips leading tokens — a title can't appear mid-name for a real
    person, and this must never empty out a name that is nothing but a title
    (e.g. "DR" alone, a sentinel-like edge case) since an empty canonical name
    is worse than an unstripped one.
    """
    tokens = name.split(" ")
    i = 0
    while i < len(tokens) and re.sub(r"[.,]", "", tokens[i]) in _TITLE_TOKENS:
        i += 1
    stripped = " ".join(tokens[i:])
    return stripped if stripped else name


def basic_normalize(name) -> str:
    """Uppercase/trim/collapse-whitespace a recipient name, drop a leading
    honorific title, and let sentinels pass through untouched.

    Underscores are treated as word separators (collapsed to a space) before
    whitespace collapsing, not as meaningful characters — SENTINELS use
    underscores as a plain constant-naming convention (checked separately,
    before this), but in an actual recipient name an underscore is just a
    punctuation artifact of how the bank rendered the same name differently
    across transactions (e.g. "SNOW_CRE" vs "SNOW CRE" for one merchant).
    Left uncollapsed, that single-character difference tanks the fuzzy
    similarity score (token_sort_ratio=50, same as an unrelated pair) and
    the two spellings never merge downstream — collapsing it here fixes the
    root cause instead of tuning a threshold to paper over it.
    """
    if pd.isna(name):
        return "UNKNOWN"
    name = str(name).strip()
    if name in SENTINELS:
        return name
    name = _SEPARATOR_RE.sub(" ", name.upper())
    name = _WHITESPACE_RE.sub(" ", name)
    name = _strip_titles(name)
    return name.strip()


def group_by_upi_id(
    df: pd.DataFrame,
    name_col: str = "_basic",
    upi_col: str = "UPI_ID",
    min_name_similarity: int = 55,
    max_distinct_names: int = 5,
) -> dict:
    """Map row index -> canonical name via exact UPI_ID grouping.

    Only covers rows that have a usable (non-null, non-"N/A") UPI_ID.

    Some extracted UPI IDs are truncated payment-gateway/QR session codes
    (e.g. "paytmqr8", "paytm.s1", "bharatpe") rather than a stable
    per-merchant identifier — the same code gets reused across genuinely
    unrelated recipients, sometimes dozens of them. A raw count-majority
    vote doesn't catch this reliably: a single stray row still clears an
    80%-purity bar once the dominant name repeats a handful of times, and
    conversely a genuine 1-vs-1 tie (two rows, same UPI ID, both legitimate
    spelling variants of one payee) has no majority at all and would be
    rejected outright even though it should merge.

    Two checks, verified against real data:

    1. `max_distinct_names` — a real per-payee UPI handle only ever shows a
       handful of spelling variants for one person (observed max: 3 on this
       dataset); a shared gateway code fans out across dozens of unrelated
       payees (observed min: 11, e.g. "paytmqr2" covers 43 distinct names).
       There's a clean gap between those, so a group with more distinct
       names than this is almost certainly a shared code, not a payee
       identifier, and is skipped entirely — its rows fall through to fuzzy
       clustering against the whole dataset instead. This also protects the
       similarity check below: fuzzy-clustering a large, diverse name list
       inevitably produces a few coincidental matches above any reasonable
       threshold just by how many pairs there are to compare.
    2. `min_name_similarity` — for the (now capped, small) remaining
       groups, legitimate shared-UPI rows are near-duplicate spellings of
       the same name (fuzzy score >= ~60 in every case observed, since the
       UPI handle itself is usually name-derived, e.g. "shloksm2" for
       "SHLOK SA"), while a coincidental collision is an unrelated name
       (fuzzy score <= ~50 in every case observed, e.g. "BIKANER" vs
       "SUNIL SH" both under "paytmqr67"). Each group is fuzzy-clustered
       internally (same complete-linkage clustering as the fallback tier
       below, just scoped to this one group) rather than majority-voted, so
       a tied group still merges correctly when the names are genuinely
       similar, and still splits correctly when they aren't.
    """
    mapping = {}
    upi_key = df[upi_col].astype(str).str.strip().str.lower()
    has_upi = df[upi_col].notna() & (upi_key != "n/a") & (upi_key != "")
    for _, group in df[has_upi].groupby(upi_key[has_upi]):
        names = group[name_col]
        names = names[~names.isin(SENTINELS)]
        if names.empty or names.nunique() > max_distinct_names:
            continue
        clusters = cluster_by_fuzzy_name(names.tolist(), threshold=min_name_similarity)
        for idx, name in group[name_col].items():
            if name not in SENTINELS:
                mapping[idx] = clusters[name]
    return mapping


def _first_token(name: str) -> str:
    """First non-title token of a name (skips Mr/Mrs/Ms/Dr/Miss — defensive,
    since basic_normalize now strips these already; kept for callers that pass
    names not routed through basic_normalize, e.g. this module's own tests)."""
    for tok in re.sub(r"[.,]", "", name).split():
        if tok.upper() not in _TITLE_TOKENS:
            return tok.upper()
    return ""


def _first_names_conflict(a: str, b: str) -> bool:
    """True if two names unambiguously belong to different people/entities.

    Two real people can share a payment channel (e.g. a family UPI ID shared
    between spouses), and their full names can still score high on token-level
    fuzzy similarity if they share a surname ("PRACHI SAMEER SAWANT" vs
    "YASH SAMEER SAWANT" share 2 of 3 tokens). Fuzzy similarity alone can't
    tell them apart, but their first names unambiguously can — so this is
    checked independently of the similarity score and forces those two names
    into different clusters no matter how similar the rest of the string is.

    Deliberately narrow to avoid false positives: only fires when the first
    tokens differ AND neither is a prefix of the other (so truncation
    variants of the SAME name, e.g. "SAM" vs "SAMEER", are still allowed to
    merge). Company names ("AIRTEL" vs "BHARTI AIRTEL") aren't first-name
    patterns and rarely collide here since they don't usually share a UPI ID
    with an unrelated company; genuine remaining cases are still catchable
    via `find_prefix_variants` + a manually-reviewed alias.
    """
    ta, tb = _first_token(a), _first_token(b)
    if not ta or not tb or ta == tb:
        return False
    return not (ta.startswith(tb) or tb.startswith(ta))


def _pairwise_scores(unique_names: list) -> dict:
    """Every unique-name pair's fuzzy similarity, keyed `(a, b)` with `a < b`
    lexicographically -- the same computation `cluster_by_fuzzy_name` needs for its
    distance matrix, extracted so a second caller (`find_ambiguous_fuzzy_pairs`) can
    inspect the same scores instead of them being discarded as local variables.

    A first-name-conflicting pair (`_first_names_conflict`) scores 0 here too, the
    same forced-max-distance treatment `cluster_by_fuzzy_name` gives them -- those
    pairs are a confident "different person" verdict, never an ambiguous review
    candidate.
    """
    scores = {}
    for a, b in itertools.combinations(sorted(set(unique_names)), 2):
        scores[(a, b)] = 0 if _first_names_conflict(a, b) else fuzz.token_sort_ratio(a, b)
    return scores


def _score_for(scores: dict, a: str, b: str) -> int:
    return scores[(a, b)] if a < b else scores[(b, a)]


def cluster_by_fuzzy_name(names: list, threshold: int = 90) -> dict:
    """Cluster a list of normalized names by fuzzy similarity (complete linkage).

    Complete linkage requires every pair within a cluster to meet `threshold`,
    which avoids the single-linkage "chaining" failure mode (A~B~C merged
    transitively even though A and C are unrelated) that connected-components
    clustering is prone to, especially with short truncated names.

    A conflicting-first-name pair (see `_first_names_conflict`) is forced to
    maximum distance regardless of its raw similarity score, so two different
    real people sharing a payment channel are never merged by this tier.

    Returns {name: canonical_name}; canonical = most frequent variant
    among `names` (ties broken by shorter string).
    """
    unique_names = list(dict.fromkeys(names))
    counts = Counter(names)

    if len(unique_names) <= 1:
        return {name: name for name in unique_names}

    scores = _pairwise_scores(unique_names)
    n = len(unique_names)
    distance = np.zeros((n, n))
    for i in range(n):
        for j in range(i + 1, n):
            score = _score_for(scores, unique_names[i], unique_names[j])
            distance[i, j] = distance[j, i] = 100 - score

    condensed = squareform(distance, checks=False)
    linkage_matrix = linkage(condensed, method="complete")
    labels = fcluster(linkage_matrix, t=100 - threshold, criterion="distance")

    clusters: dict = {}
    for name, label in zip(unique_names, labels):
        clusters.setdefault(label, []).append(name)

    mapping = {}
    for members in clusters.values():
        canonical = max(members, key=lambda nm: (counts[nm], -len(nm)))
        for name in members:
            mapping[name] = canonical
    return mapping


def find_ambiguous_fuzzy_pairs(names: list, clusters: dict, threshold: int = 90, review_floor: int = 78) -> list:
    """Near-miss pairs from the same fuzzy-similarity data `cluster_by_fuzzy_name` uses,
    for the Merge Payees human-review feature (2026-07-19) -- reuses an ALREADY-COMPUTED
    `clusters` mapping (that function's own output for these same `names`) rather than
    re-deriving it, since scipy's hierarchical linkage is the expensive part of that
    function and must only run once per request.

    A pair scoring in `[review_floor, threshold)` that landed in different clusters is
    close enough to plausibly be the same payee, not close enough to auto-merge --
    exactly the open question this feature asks the user. First-name-conflict pairs
    (scored 0 by `_pairwise_scores`) are excluded entirely: those are already a confident
    "different person" verdict, not an ambiguous one.

    Pairs are reported at the CLUSTER level (`clusters[a]`/`clusters[b]`, i.e. each
    raw variant's own canonical name), deduplicated, keeping the best (max) raw score
    observed between any two variants of the two clusters -- so three near-miss raw
    spellings against one other cluster surface as one ambiguous pair, not three.
    """
    unique_names = list(dict.fromkeys(names))
    if len(unique_names) <= 1:
        return []

    scores = _pairwise_scores(unique_names)
    best_by_cluster_pair: dict = {}
    for (a, b), score in scores.items():
        if not (review_floor <= score < threshold):
            continue
        cluster_a, cluster_b = clusters.get(a), clusters.get(b)
        if cluster_a is None or cluster_b is None or cluster_a == cluster_b:
            continue
        key = (cluster_a, cluster_b) if cluster_a < cluster_b else (cluster_b, cluster_a)
        best_by_cluster_pair[key] = max(best_by_cluster_pair.get(key, 0), score)

    return [{"name_a": a, "name_b": b, "score": s} for (a, b), s in best_by_cluster_pair.items()]


def normalize_recipients(
    df: pd.DataFrame,
    threshold: int = 90,
    name_col: str = "Recipient_Name",
    upi_col: str = "UPI_ID",
    min_upi_name_similarity: int = 55,
) -> pd.Series:
    """Top-level entry point: canonical recipient names aligned to df.index.

    Combines exact UPI_ID grouping (tier 1) with fuzzy name clustering
    among the leftover rows (tier 2). `UNKNOWN`/`PHONE_TRANSFER` sentinels
    are left untouched.
    """
    basic = df[name_col].apply(basic_normalize)
    work = df.assign(_basic=basic)

    canonical = basic.copy()

    upi_mapping = group_by_upi_id(
        work,
        name_col="_basic",
        upi_col=upi_col,
        min_name_similarity=min_upi_name_similarity,
    )
    for idx, name in upi_mapping.items():
        canonical.loc[idx] = name

    covered = set(upi_mapping.keys())
    remaining_mask = ~basic.index.isin(covered) & ~basic.isin(SENTINELS)
    remaining_names = basic[remaining_mask].tolist()
    if remaining_names:
        fuzzy_mapping = cluster_by_fuzzy_name(remaining_names, threshold=threshold)
        canonical.loc[remaining_mask] = basic[remaining_mask].map(fuzzy_mapping)

    return canonical


def find_prefix_variants(names, min_len: int = 6) -> list:
    """Find canonical-name pairs where one is a literal prefix of the other.

    Both tiers above compare names with length-penalized fuzzy metrics
    (token_sort_ratio/ratio), so a short truncated name scores far below
    any reasonable threshold against its own untruncated form (e.g.
    "PRACHI S" vs "MRS. PRACHI SAMEER SAW" scores 53) and the two never
    merge, even though they're clearly the same payee. A substring-anywhere
    metric like partial_ratio "fixes" that but is far too permissive at
    this string length: short/common fragments ("SON", "ANAS", "DON")
    score 75-100 against dozens of unrelated names, and it even resurrects
    collisions the UPI-similarity gate above was built to reject (e.g.
    "JULFIKAR"/"BIKANER" back at 72.7).

    A literal-prefix check with a minimum length is a much narrower, lower-
    noise middle ground: it only fires when one name starts with the other
    verbatim, which is exactly what bank-statement truncation produces.
    Not merged automatically — some hits are still genuinely ambiguous
    without knowing the account holder's actual contacts (e.g. "KRISHNA"
    is a common enough first name that "KRISHNA"/"KRISHNAM" could be two
    different people) — so this returns candidate pairs for manual review,
    the same way the notebook's multi-variant QA cell does.
    """
    pairs = []
    for a, b in itertools.combinations(sorted(set(names)), 2):
        short, long_ = (a, b) if len(a) <= len(b) else (b, a)
        if len(short) >= min_len and short != long_ and long_.startswith(short):
            pairs.append((short, long_))
    return pairs


def _chains(a: str, b: str) -> bool:
    """Whitespace-tolerant prefix check: True if one name is a prefix of the other
    once spaces are removed.

    Plain `str.startswith` misses same-payee pairs where extraction inserted a stray
    space (e.g. "SAMEER B ALIRAM" vs "SAMEER BALIRAM SAWA" -- same person, differ only
    by that one space), while still correctly rejecting two different people who merely
    share a leading word (e.g. "MAHENDRA DUNGARS" vs "MAHENDRA KAILAS KOLI" diverge
    right after "MAHENDRA" even with spaces stripped).
    """
    sa, sb = a.replace(" ", ""), b.replace(" ", "")
    return sa.startswith(sb) or sb.startswith(sa)


def _prefix_components(names, min_len: int = 6) -> list:
    """Connected components of `names` under the prefix-variant relation
    (`find_prefix_variants`) -- shared by `merge_prefix_chains` (which merges the
    unambiguous members) and `find_prefix_ambiguities` (which surfaces the ambiguous
    ones instead of discarding them). Grouping only; deciding what to do with each
    component is each caller's own job.
    """
    names = list(dict.fromkeys(names))
    pairs = find_prefix_variants(names, min_len=min_len)

    # Union-find over names connected by a prefix-variant edge, for grouping only.
    parent = {n: n for n in names}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x, y):
        rx, ry = find(x), find(y)
        if rx != ry:
            parent[rx] = ry

    for short, long_ in pairs:
        union(short, long_)

    components: dict = {}
    for n in names:
        components.setdefault(find(n), []).append(n)
    return list(components.values())


def _maximal_members(members: list) -> list:
    """Members with nothing strictly longer in the component chaining from them --
    shared by `merge_prefix_chains` and `find_prefix_ambiguities` so both agree on
    what counts as a candidate "true form"."""
    return [m for m in members if not any(len(o) > len(m) and _chains(m, o) for o in members if o != m)]


def merge_prefix_chains(names, min_len: int = 6) -> dict:
    """Safely auto-merge truncation-prefix variants of canonical names.

    Groups `names` into connected components under the prefix-variant relation
    (`_prefix_components`). Within a component, some names may be "maximal" -- nothing
    longer in the component chains from them, so they're candidate true forms. A
    non-maximal member merges into a maximal one ONLY if it chains with EXACTLY
    ONE maximal name in the component; otherwise it's ambiguous and is left as
    itself, mapped to nothing.

    This "exactly one" rule is what keeps it safe on two different failure modes
    found on real data:

    - Two different real people/entities sharing a short common prefix (e.g.
      "MAHENDRA DUNGARS" vs "MAHENDRA KAILAS KOLI") are both maximal (neither
      chains into the other), so bare "MAHENDRA" chains with BOTH of them and is
      correctly left unmerged rather than arbitrarily attributed to whichever
      happens to have the longer string.
    - A component can contain one genuine outlier that shares a short prefix but
      isn't actually a truncation of the "main" chain (e.g. "SAMEER SAWANT" omits
      the middle name "Baliram" entirely, so it doesn't chain with "SAMEER
      BALIRAM SAWA" even though both reduce from "SAMEER" and are the same real
      person) -- it becomes its own second maximal name, and every OTHER member
      that chains only with the main chain still merges correctly; only the
      genuine outlier (and any name ambiguous between the two) stays unmerged.

    Returns {name: canonical_name} for every name passed in (unmerged names map
    to themselves). Call `find_prefix_ambiguities` if you want the discarded
    ambiguous cases themselves (e.g. for the Merge Payees human-review feature)
    rather than just knowing they were left unmerged.
    """
    mapping = {}
    for members in _prefix_components(names, min_len=min_len):
        if len(members) == 1:
            mapping[members[0]] = members[0]
            continue

        maximal = _maximal_members(members)
        for m in members:
            if m in maximal:
                mapping[m] = m
                continue
            targets = [mx for mx in maximal if _chains(m, mx)]
            mapping[m] = targets[0] if len(targets) == 1 else m
    return mapping


def find_prefix_ambiguities(names, min_len: int = 6) -> list:
    """The `len(targets) != 1` cases `merge_prefix_chains` discards, for the Merge
    Payees human-review feature (2026-07-19) -- a name that's ambiguous between zero
    or two-or-more competing "maximal" forms in its prefix-chain component, rather
    than cleanly merging into exactly one.

    Reuses the exact same components/maximal-member logic `merge_prefix_chains` uses
    (`_prefix_components`, `_maximal_members`) so the two functions can never
    disagree about what counts as ambiguous.

    Returns `[{"name": <ambiguous member>, "targets": [<competing maximal forms>]}]`.
    Maximal names themselves, and non-maximal members with exactly one target
    (already auto-merged by `merge_prefix_chains`), are excluded -- there's nothing
    to ask the user about those.
    """
    ambiguities = []
    for members in _prefix_components(names, min_len=min_len):
        if len(members) == 1:
            continue
        maximal = _maximal_members(members)
        for m in members:
            if m in maximal:
                continue
            targets = [mx for mx in maximal if _chains(m, mx)]
            if len(targets) != 1:
                ambiguities.append({"name": m, "targets": targets})
    return ambiguities


def _squash(name: str) -> str:
    """Uppercase, alphanumeric-only, spaces removed -- the comparison form for the
    enriched (Phase A, ADR-019) detectors below, which key on the *character run* of
    a name rather than its whitespace-delimited tokens.

    Bank/UPI truncation and handle-style names ("AASHAYJ2", "VIHAANSHINDE18") mangle
    exactly the token structure `token_sort_ratio` and the prefix tier rely on, so
    those two tiers miss them; the squashed form is what makes a name-derived UPI
    handle comparable to the spelled name it came from.
    """
    return re.sub(r"[^A-Z0-9]", "", str(name).upper())


def find_upi_handle_pairs(names, min_token_len: int = 5) -> list:
    """Review candidates (Phase A, ADR-019) where a spaceless, name-derived UPI
    handle ("AASHAYJ2", "VIHAANSHINDE18") begins with another name's spelled first
    token ("AASHAY MAKRAND JADHAV", "VIHAAN SACHIN").

    This is the generalizable pattern behind a large class of misses the token- and
    prefix-based tiers can't catch: a handle interleaves the first name with surname
    initials and digits, so it's neither a literal prefix of the full name nor a
    high `token_sort_ratio` match -- but its leading character run IS the full name's
    first token. `min_token_len` keeps that anchoring token long enough (>=5) that a
    coincidental short-prefix collision doesn't fire this tier (short truncations are
    `find_short_prefix_pairs`'s job instead). Never auto-merges -- pure review
    surfacing, so a wrong guess costs the user one "different" tap, not a bad merge.
    """
    names = list(dict.fromkeys(names))
    pairs = []
    for a, b in itertools.combinations(names, 2):
        for handle, spelled in ((a, b), (b, a)):
            if handle in SENTINELS or spelled in SENTINELS:
                continue
            if " " in handle or " " not in spelled:
                continue
            token = _first_token(spelled)
            if len(token) >= min_token_len and _squash(handle).startswith(_squash(token)):
                pairs.append({"anchor": spelled, "candidate": handle, "score": 90, "reason": "upi_handle_variant"})
    return pairs


def find_short_prefix_pairs(names, min_len: int = 3, max_len: int = 5) -> list:
    """Review candidates (Phase A, ADR-019) for bare truncated first names too short
    for the `merge_prefix_chains` tier's `min_len=6` floor -- "YASH" -> "YASH SAMEER
    SAWANT", "VIHAA" -> "VIHAAN SACHIN", "ALP" -> "ALPHA VIJAY RANE".

    Deliberately bounded to `[min_len, max_len]` = `[3, 5]`, i.e. exactly the sub-6
    gap the prefix tier can't touch (6+ is already handled there), so the two never
    overlap or double-surface. Only a single-token candidate that prefixes the other
    name's *first token* qualifies -- a bare truncated first name, not an arbitrary
    substring -- which keeps this from firing on every short character run in a large
    identity set. A 3-char prefix is a weak signal on its own, which is precisely why
    this only ever routes to human review, never to an auto-merge.
    """
    names = list(dict.fromkeys(names))
    pairs = []
    for a, b in itertools.combinations(names, 2):
        for short, long_ in ((a, b), (b, a)):
            if short in SENTINELS or long_ in SENTINELS or short == long_ or " " in short:
                continue
            s = _squash(short)
            if not (min_len <= len(s) <= max_len):
                continue
            if _squash(_first_token(long_)).startswith(s):
                pairs.append({"anchor": long_, "candidate": short, "score": 80, "reason": "short_prefix"})
    return pairs


def _cluster_sizes(clusters: dict, occurrence_counts: Counter) -> Counter:
    """Total raw occurrence count per cluster canonical value -- sum of every raw
    variant's own count, not just the winning variant's -- used by
    `_build_ambiguous_groups` to pick which side of a fuzzy-near-miss pair is the
    more established "anchor" (same frequency-based tie-break `cluster_by_fuzzy_name`
    already uses for choosing a canonical display name)."""
    sizes: Counter = Counter()
    for variant, canonical_name in clusters.items():
        sizes[canonical_name] += occurrence_counts[variant]
    return sizes


def _build_ambiguous_groups(
    fuzzy_ambiguous: list,
    fuzzy_cluster_sizes: Counter,
    prefix_ambiguous: list,
    name_to_keys: dict,
    extra_pairs: list = (),
) -> list:
    """Combines every tier's ambiguous name-pairs into anchor/candidate groups keyed
    by entry key (not bare name) -- every candidate sharing an anchor lands in one
    group, so the Merge Payees review queue shows one anchor with all its open
    questions together rather than one row per pair.

    Fuzzy-near-miss anchor/candidate assignment: the more frequent cluster is the
    anchor (ties broken by shorter string), mirroring `cluster_by_fuzzy_name`'s own
    canonical-name tie-break. Prefix-ambiguous pairs already have a natural anchor
    per `find_prefix_ambiguities`'s own framing -- each competing "target" is a
    candidate anchor for the ambiguous (shorter) member. `extra_pairs` (Phase A,
    ADR-019: handle / short-prefix / similar-UPI detectors) arrive pre-oriented as
    `{"anchor", "candidate", "score", "reason"}`, added last so a pair an earlier,
    higher-signal tier already surfaced keeps that tier's reason (add() dedups by
    candidate within a group).
    """
    groups: dict = {}

    def add(anchor: str, candidate: str, score: int, reason: str) -> None:
        if anchor == candidate or anchor not in name_to_keys or candidate not in name_to_keys:
            return
        group = groups.setdefault(anchor, {"anchor_key": name_to_keys[anchor][0], "anchor_name": anchor, "candidates": []})
        if any(c["name"] == candidate for c in group["candidates"]):
            return
        group["candidates"].append({"key": name_to_keys[candidate][0], "name": candidate, "score": score, "reason": reason})

    for pair in fuzzy_ambiguous:
        a, b, score = pair["name_a"], pair["name_b"], pair["score"]
        anchor, candidate = sorted([a, b], key=lambda nm: (-fuzzy_cluster_sizes[nm], len(nm)))
        add(anchor, candidate, score, "fuzzy_near_miss")

    for item in prefix_ambiguous:
        for target in item["targets"]:
            add(target, item["name"], 100, "prefix_ambiguous")

    for pair in extra_pairs:
        add(pair["anchor"], pair["candidate"], pair["score"], pair["reason"])

    return list(groups.values())


def canonicalize_with_ambiguities(
    entries: list[dict],
    threshold: int = 90,
    min_upi_name_similarity: int = 55,
    review_floor: int = 78,
    min_len: int = 6,
) -> dict:
    """Runs the same single clustering pass `canonicalize()` does and additionally
    collects ambiguous anchor/candidate groups for human review (Merge Payees
    feature, 2026-07-19) -- pairs the algorithm considered but did not confidently
    auto-merge. `canonicalize()` is defined in terms of this function's
    `"canonical_names"` key so the two can never drift apart.

    `review_floor=78` is a starting value, not empirically validated against real
    data the way `threshold=90`/`min_upi_name_similarity=55` were -- expect to
    recalibrate from real usage, same posture ADR-014's override mechanism already
    established for tuning after the fact rather than guessing right up front.

    Returns `{"canonical_names": {key: name}, "ambiguous_groups": [{"anchor_key",
    "anchor_name", "candidates": [{"key", "name", "score", "reason"}]}]}`.
    """
    if not entries:
        return {"canonical_names": {}, "ambiguous_groups": []}

    df = pd.DataFrame(entries).set_index("key")
    basic = df["recipient_name"].apply(basic_normalize)
    work = df.assign(_basic=basic)

    canonical = basic.copy()
    upi_mapping = group_by_upi_id(
        work, name_col="_basic", upi_col="upi_id", min_name_similarity=min_upi_name_similarity
    )
    for idx, name in upi_mapping.items():
        canonical.loc[idx] = name

    covered = set(upi_mapping.keys())
    remaining_mask = ~basic.index.isin(covered) & ~basic.isin(SENTINELS)
    remaining_names = basic[remaining_mask].tolist()

    fuzzy_ambiguous: list = []
    fuzzy_cluster_sizes: Counter = Counter()
    if remaining_names:
        fuzzy_clusters = cluster_by_fuzzy_name(remaining_names, threshold=threshold)
        canonical.loc[remaining_mask] = basic[remaining_mask].map(fuzzy_clusters)
        fuzzy_ambiguous = find_ambiguous_fuzzy_pairs(
            remaining_names, fuzzy_clusters, threshold=threshold, review_floor=review_floor
        )
        fuzzy_cluster_sizes = _cluster_sizes(fuzzy_clusters, Counter(remaining_names))

    # name -> entry keys, from the same intermediate `canonical` series both tiers'
    # ambiguity data refer to (tier 3 operates on canonical.unique() below).
    name_to_keys: dict = {}
    for key, name in canonical.items():
        name_to_keys.setdefault(name, []).append(key)

    unique_canonical = canonical.unique().tolist()
    chain_mapping = merge_prefix_chains(unique_canonical, min_len=min_len)
    prefix_ambiguous = find_prefix_ambiguities(unique_canonical, min_len=min_len)
    final_canonical = canonical.map(chain_mapping)

    # Enriched review candidates (Phase A, ADR-019): generalizable same-payee signals
    # the tiers above leave unmerged -- name-derived UPI handles ("AASHAYJ2") and
    # sub-min_len prefix truncations ("ALP"). Surfaced for Merge Payees human review
    # only, never auto-applied to canonical_names.
    extra_pairs = find_upi_handle_pairs(unique_canonical) + find_short_prefix_pairs(unique_canonical)
    # Never surface a pair tier-3 (merge_prefix_chains) already collapsed into one
    # canonical -- that question is settled, not open.
    extra_pairs = [
        p
        for p in extra_pairs
        if chain_mapping.get(p["anchor"], p["anchor"]) != chain_mapping.get(p["candidate"], p["candidate"])
    ]

    ambiguous_groups = _build_ambiguous_groups(
        fuzzy_ambiguous, fuzzy_cluster_sizes, prefix_ambiguous, name_to_keys, extra_pairs
    )

    return {"canonical_names": final_canonical.to_dict(), "ambiguous_groups": ambiguous_groups}


def canonicalize(entries: list[dict], threshold: int = 90, min_upi_name_similarity: int = 55) -> dict[str, str]:
    """Full pipeline entry point for the FastAPI route: takes a list of
    {key, recipient_name, upi_id} dicts for one user (the algorithm clusters
    across a user's full recipient set at once, so must be called per user,
    never with multiple users' entries mixed together), returns
    {key: canonical_name}.

    Runs UPI grouping + fuzzy clustering (tiers 1-2) followed by the
    truncation-prefix merge (tier 3) as a post-pass over the resulting canonical
    values, matching the offline pipeline's stage order exactly. Defined in terms
    of `canonicalize_with_ambiguities` so the two can never drift apart.
    """
    return canonicalize_with_ambiguities(entries, threshold=threshold, min_upi_name_similarity=min_upi_name_similarity)[
        "canonical_names"
    ]
