"""training/dataset_locator.py — no-hardcoded-filename dataset discovery
(ML strategy phase, 2026-07-19, ADR-017)."""

import time

import pytest

from training.dataset_locator import NoLabeledDatasetFoundError, find_latest_dataset_file


def test_finds_the_only_candidate_file(tmp_path) -> None:
    (tmp_path / "SpendWise_Final_Labeled.xlsx").write_bytes(b"fake xlsx bytes")

    found = find_latest_dataset_file(tmp_path)

    assert found == tmp_path / "SpendWise_Final_Labeled.xlsx"


def test_picks_the_most_recently_modified_of_several_candidates(tmp_path) -> None:
    older = tmp_path / "old_export.csv"
    newer = tmp_path / "new_export.xlsx"
    older.write_bytes(b"old")
    time.sleep(0.01)
    newer.write_bytes(b"new")

    assert find_latest_dataset_file(tmp_path) == newer


def test_extension_match_is_case_insensitive(tmp_path) -> None:
    (tmp_path / "labeled.CSV").write_bytes(b"data")

    assert find_latest_dataset_file(tmp_path) == tmp_path / "labeled.CSV"


def test_ignores_non_dataset_files(tmp_path) -> None:
    (tmp_path / ".gitkeep").write_bytes(b"")
    (tmp_path / "notes.txt").write_bytes(b"not a dataset")
    (tmp_path / "labeled.csv").write_bytes(b"data")

    assert find_latest_dataset_file(tmp_path) == tmp_path / "labeled.csv"


def test_ignores_subdirectories(tmp_path) -> None:
    (tmp_path / "labeled.csv").write_bytes(b"data")
    subdir = tmp_path / "archive"
    subdir.mkdir()
    (subdir / "old.csv").write_bytes(b"should not be picked")

    assert find_latest_dataset_file(tmp_path) == tmp_path / "labeled.csv"


def test_raises_a_clear_error_when_no_candidate_exists(tmp_path) -> None:
    # Windows paths contain backslashes, which pytest.raises(match=...) would treat as regex
    # escapes -- assert on the message directly instead of via a match= regex.
    with pytest.raises(NoLabeledDatasetFoundError) as excinfo:
        find_latest_dataset_file(tmp_path)

    assert str(tmp_path) in str(excinfo.value)
