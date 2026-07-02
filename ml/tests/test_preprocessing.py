from training.preprocessing import build_feature_frame, extract_features


def test_extracts_all_fields_correctly() -> None:
    transaction = {
        "recipient_name": "Swiggy",
        "upi_id": "swiggy@okicici",
        "bank": "ICICI",
        "transaction_mode": "UPI",
        "amount": -350.0,
        "note": "dinner",
    }

    features = extract_features(transaction)

    assert features["recipient_name"] == "Swiggy"
    assert features["upi_id"] == "swiggy@okicici"
    assert features["bank"] == "ICICI"
    assert features["transaction_mode"] == "UPI"
    assert features["amount"] == -350.0
    assert features["note"] == "dinner"
    assert "Swiggy" in features["text"]
    assert "swiggy@okicici" in features["text"]
    assert "dinner" in features["text"]


def test_null_note_and_bank_handled() -> None:
    transaction = {
        "recipient_name": "ASIM HEM",
        "upi_id": "asimshah",
        "bank": None,
        "transaction_mode": "UPI",
        "amount": -75.0,
        "note": None,
    }

    features = extract_features(transaction)

    assert features["bank"] == ""
    assert features["note"] == ""
    assert features["recipient_name"] == "ASIM HEM"


def test_empty_transaction_with_only_amount_does_not_throw() -> None:
    transaction = {
        "recipient_name": None,
        "upi_id": None,
        "bank": None,
        "transaction_mode": None,
        "amount": 1500.0,
        "note": None,
    }

    features = extract_features(transaction)

    assert features["amount"] == 1500.0
    assert features["recipient_name"] == ""
    assert features["upi_id"] == ""
    assert features["bank"] == ""
    assert features["transaction_mode"] == ""
    assert features["note"] == ""
    assert features["text"] == ""


def test_missing_keys_and_nan_string_do_not_throw() -> None:
    features = extract_features({})
    assert features["amount"] == 0.0
    assert features["recipient_name"] == ""

    features = extract_features({"recipient_name": "nan", "amount": None})
    assert features["recipient_name"] == ""
    assert features["amount"] == 0.0

    features = extract_features({"amount": "not-a-number"})
    assert features["amount"] == 0.0


def test_build_feature_frame_shape() -> None:
    transactions = [
        {"recipient_name": "A", "amount": 10.0},
        {"recipient_name": "B", "amount": 20.0, "note": "x"},
    ]

    df = build_feature_frame(transactions)

    assert len(df) == 2
    assert list(df.columns) == ["recipient_name", "upi_id", "bank", "transaction_mode", "note", "amount", "text"]
