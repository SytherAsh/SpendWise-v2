"""training/train.py's load_labeled_dataset — format dispatch (.csv/.xlsx) and
column-name normalization (ML strategy phase, 2026-07-19, ADR-017). The real
dataset file in use as of this change (`SpendWise_Final_Labeled.xlsx`) has
PascalCase headers (`Transaction_Date`, `Recipient_Name`, `UPI_ID`, ...) while
every downstream reader (build_feature_frame, train_model, train_recurring)
expects lowercase snake_case — this is what makes that file, and any
differently-cased future export, work without a code change.
"""

import pandas as pd

from training.train import load_labeled_dataset


def test_loads_csv_by_extension(tmp_path) -> None:
    path = tmp_path / "labeled.csv"
    pd.DataFrame([{"recipient_name": "Swiggy", "amount": -300.0, "category": "Food / Dine Out"}]).to_csv(path, index=False)

    df = load_labeled_dataset(path)

    assert list(df["recipient_name"]) == ["Swiggy"]


def test_loads_xlsx_by_extension(tmp_path) -> None:
    path = tmp_path / "labeled.xlsx"
    pd.DataFrame([{"recipient_name": "Swiggy", "amount": -300.0, "category": "Food / Dine Out"}]).to_excel(path, index=False)

    df = load_labeled_dataset(path)

    assert list(df["recipient_name"]) == ["Swiggy"]


def test_normalizes_pascal_case_headers_to_snake_case(tmp_path) -> None:
    path = tmp_path / "labeled.xlsx"
    pd.DataFrame(
        [{"Transaction_Date": "2023-01-01", "Recipient_Name": "Swiggy", "UPI_ID": "swiggy@okicici", "Amount": -300.0, "category": "Food / Dine Out"}]
    ).to_excel(path, index=False)

    df = load_labeled_dataset(path)

    assert set(df.columns) >= {"transaction_date", "recipient_name", "upi_id", "amount", "category"}
    assert df.loc[0, "recipient_name"] == "Swiggy"


def test_normalizes_headers_with_spaces(tmp_path) -> None:
    path = tmp_path / "labeled.csv"
    pd.DataFrame([{"Recipient Name": "Swiggy", "Amount": -300.0, "category": "Food / Dine Out"}]).to_csv(path, index=False)

    df = load_labeled_dataset(path)

    assert "recipient_name" in df.columns
