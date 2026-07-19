"""Locates the labeled dataset file used for training/evaluation/retraining,
by directory convention instead of a hardcoded filename (ML strategy phase,
2026-07-19 -- see docs/spec/decisions.md ADR-017).

`ml/data/` is expected to hold one (or more) labeled-dataset exports, each
either produced by `ml/labeling/scripts/merge_datasets.py` (.xlsx) or placed
there manually (.csv or .xlsx). `find_latest_dataset_file` always returns
whichever one was written most recently -- replacing the file (a newer
export, a differently-named CSV) never requires a code change or a specific
filename. If more than one candidate exists, the older ones are simply
ignored, not deleted -- this module never writes to `ml/data/`.
"""

from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
SUPPORTED_EXTENSIONS = {".csv", ".xlsx"}


class NoLabeledDatasetFoundError(FileNotFoundError):
    def __init__(self, data_dir: Path):
        self.data_dir = data_dir
        super().__init__(
            f"No labeled dataset file found in {data_dir} "
            f"(looked for {sorted(SUPPORTED_EXTENSIONS)}). Place a labeled .csv or .xlsx "
            "export there -- see ml/labeling/scripts/merge_datasets.py for the expected columns."
        )


def find_latest_dataset_file(data_dir: Path = DATA_DIR) -> Path:
    """Most-recently-modified `.csv`/`.xlsx` file directly under `data_dir` (not
    recursive -- `ml/data/` has no subdirectories in normal use). Raises
    `NoLabeledDatasetFoundError` rather than returning `None`: every caller
    needs an actual file to load, so silently returning nothing would just
    move the failure one level up with a less specific error.
    """
    candidates = [p for p in data_dir.glob("*") if p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS]
    if not candidates:
        raise NoLabeledDatasetFoundError(data_dir)
    return max(candidates, key=lambda p: p.stat().st_mtime)
