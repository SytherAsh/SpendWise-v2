package com.spendwise.analytics;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Manual CSV serialization for `GET /analytics/export/csv` (E7-S2-T1) — no library needed for a flat, ~15-column export. */
final class AnalyticsCsvWriter {

    private static final String[] HEADER = {
        "id",
        "transaction_date",
        "debit",
        "credit",
        "amount",
        "balance",
        "transaction_mode",
        "dr_cr_indicator",
        "bank_transaction_id",
        "recipient_name",
        "bank",
        "upi_id",
        "note",
        "source",
        "category_id",
        "category_name"
    };

    private AnalyticsCsvWriter() {}

    static byte[] write(List<AnalyticsExportRow> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, HEADER);
        for (AnalyticsExportRow row : rows) {
            appendRow(
                    csv,
                    row.id().toString(),
                    row.transactionDate().toString(),
                    row.debit().toPlainString(),
                    row.credit().toPlainString(),
                    row.amount().toPlainString(),
                    row.balance() == null ? "" : row.balance().toPlainString(),
                    nullToEmpty(row.transactionMode()),
                    nullToEmpty(row.drCrIndicator()),
                    nullToEmpty(row.bankTransactionId()),
                    nullToEmpty(row.recipientName()),
                    nullToEmpty(row.bank()),
                    nullToEmpty(row.upiId()),
                    nullToEmpty(row.note()),
                    nullToEmpty(row.source()),
                    row.categoryId() == null ? "" : row.categoryId().toString(),
                    nullToEmpty(row.categoryName()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields[i]));
        }
        csv.append("\r\n");
    }

    private static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
