# ML Service — FastAPI (Python)

The machine learning categorization service for SpendWise. Receives structured transaction data and predicts which of 12 predefined spending categories it belongs to.

## Tech

- **Framework**: FastAPI
- **Language**: Python 3.10+
- **ML**: scikit-learn (lightweight classifier — exact model TBD during training)
- **Data**: pandas, numpy
- **Tests**: pytest

## Structure

```
ml/
├── api/            FastAPI routes (/predict, /retrain, /evaluate)
├── models/         Trained model artifacts (.pkl or .joblib) — gitignored
├── training/       Training scripts, preprocessing pipeline, feature engineering
├── evaluation/     Accuracy evaluation script (F1, precision, recall, confusion matrix)
└── data/           Training datasets (gitignored — not committed to repo)
    └── spendwise_labeled.xlsx   1,810 labeled transactions, produced by ml/labeling/
                                 (see ml/labeling/README.md for the labeling pipeline
                                 and ml/labeling/tracking/LABELING_STATUS.md for provenance)
```

## API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/predict` | Predict category for a transaction |
| POST | `/retrain` | Trigger batch retraining with latest corrections |
| GET | `/evaluate` | Run accuracy evaluation, return metrics |
| GET | `/health` | Health check |

### Prediction Request

```json
{
  "recipient_name": "Swiggy",
  "upi_id": "swiggy@okicici",
  "bank": "ICICI",
  "transaction_mode": "UPI",
  "amount": -350.0,
  "note": null
}
```

### Prediction Response

```json
{
  "category_id": 7,
  "category_name": "Food / Dine Out",
  "confidence": 0.94
}
```

## Categories (12)

| ID | Name |
|---|---|
| 1 | Shopping |
| 2 | Entertainment |
| 3 | Sports & Fitness |
| 4 | Groceries |
| 5 | Travel |
| 6 | Miscellaneous |
| 7 | Food / Dine Out |
| 8 | Cosmetics |
| 9 | Subscriptions |
| 10 | Transfers |
| 11 | Medical |
| 12 | Fees & Debt |

## Training Pipeline

The baseline model is trained on 1,810 labeled transactions from a 4-year
personal SBI bank statement, labeled via the pipeline in `ml/labeling/`
(rules-based auto-labeling + an interactive interview for the long tail —
see `ml/labeling/README.md`). New training data comes from user corrections
stored in the `ml_corrections` Supabase table.

Category balance is uneven — Transfers is ~50% of the data (expected, P2P
transfers are naturally frequent) and Sports & Fitness has zero examples in
this dataset. Expect to need class weighting; see
`ml/labeling/tracking/LABELING_STATUS.md` for the full distribution.

```bash
cd ml
python training/train.py --data data/spendwise_labeled.xlsx
```

## Evaluation

```bash
python evaluation/evaluate.py --data data/spendwise_labeled.xlsx
```

Outputs: accuracy, precision, recall, F1-score per category, confusion matrix. Reports saved to `evaluation/reports/`.

## Running Locally

```bash
pip install -r requirements.txt
uvicorn api.main:app --reload --port 8000
```

Service at `http://localhost:8000`

## Running Tests

```bash
pytest tests/ -v
```

## Key Decisions

- Server-side inference — ML does not run on the Android device. See [docs/decisions.md](../docs/decisions.md) ADR-004.
- **Scope boundary**: this service predicts exactly the 12 categories above — nothing
  else. "Who" a Transfers recipient is (friend, family, self, merchant, etc.) is
  deliberately kept out of the model as a separate, not-yet-built enrichment concept.
  See ADR-010. Don't add counterparty fields to the `/predict` request/response schema
  or split `Transfers` into more classes without revisiting that ADR first.
