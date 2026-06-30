# Android — Kotlin Native App

The Android app for SpendWise. Reads SMS transaction alerts, parses them on-device, and syncs structured data to the backend.

## Tech

- **Language**: Kotlin (native Android)
- **Min SDK**: TBD during implementation (priority is SMS API compatibility)
- **Local DB**: Room (offline transaction queue)
- **Auth**: Firebase Authentication (OTP + Google)
- **Distribution**: Firebase App Distribution (MVP) → Google Play Store (future)

## Module Structure

```
app/src/main/kotlin/com/spendwise/
├── sms/            BroadcastReceiver for incoming SMS, foreground service lifecycle
├── parser/         Regex rules (SBI, Paytm, GPay), keyword detector, field extractor
├── sync/           Local Room DB queue, batch HTTP upload, retry on reconnect
├── ui/             Android screens: dashboard, transactions, chatbot, settings
└── storage/        Room database definition and DAOs for offline queue
```

## SMS Parsing Scope (MVP)

| Sender | Format support |
|---|---|
| SBI (State Bank of India) | Debit, Credit, UPI, IMPS |
| Paytm | UPI payments |
| GPay (Google Pay) | UPI payments |

Unknown senders: keyword-based field extraction (best-effort, may have null fields).

## Running Locally

1. Open `android/` in Android Studio
2. Create `android/local.properties` and set `FIREBASE_APP_ID` and `API_BASE_URL`
3. Run on emulator or physical device (API 26+)

## Running Tests

```bash
./gradlew test    # runs Kotlin unit tests (parser, deduplication logic)
```

## Key Behavior

- **Foreground service**: runs continuously, survives app close, required to avoid Android Doze killing the service
- **Backfill on first launch**: reads entire SMS inbox history to populate past transactions
- **Deduplication**: two-layer — `transaction_id` primary key + `(upi_id, amount, timestamp)` composite
- **Offline queue**: transactions stored in Room DB when backend is unreachable; auto-retry every 15–30 min
- **Raw SMS never sent to server**: only structured fields transmitted — see [docs/decisions.md](../docs/decisions.md) ADR-002

## Design

Shares visual language with the web dashboard — same color palette and typography. See `ui/theme/` for tokens.
