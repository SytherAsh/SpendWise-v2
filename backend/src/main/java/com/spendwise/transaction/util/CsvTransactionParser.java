package com.spendwise.transaction.util;

import com.spendwise.ingest.dto.IngestTransactionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for bank statement CSV files into ingest-compatible transaction items.
 * Supports demo data and future user-uploaded bank statements.
 *
 * CSV format (from data/demo-transactions.csv):
 * transaction_date, debit, credit, amount, dr_cr_indicator, transaction_id,
 * recipient_name, upi_id, bank, transaction_mode, note, source, category
 *
 * Fields used by ingest:
 * - transaction_date: ISO 8601 timestamp
 * - debit: positive amount (0 if credit)
 * - credit: positive amount (0 if debit)
 * - amount: signed (negative=debit, positive=credit)
 * - dr_cr_indicator: 'DR' or 'CR'
 * - transaction_id: unique identifier
 * - recipient_name: payee/payer name
 * - upi_id: UPI identifier (nullable)
 * - bank: bank code (nullable)
 * - transaction_mode: UPI, INB, IMPS, NEFT (nullable)
 * - note: optional description
 * - source: 'sms', 'bank_statement', 'manual' (forced to 'bank_statement' by ingest)
 *
 * The 'category' column is for reference only and not sent to ingest.
 */
@Component
public class CsvTransactionParser {

    private static final Logger log = LoggerFactory.getLogger(CsvTransactionParser.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Parse a CSV input stream into a list of ingest transaction items.
     *
     * @param inputStream CSV file input stream
     * @return List of parsed transactions ready for ingest
     * @throws CsvParseException if parsing fails
     */
    public List<IngestTransactionItem> parse(InputStream inputStream) throws CsvParseException {
        List<IngestTransactionItem> transactions = new ArrayList<>();
        Map<String, Integer> headerMap = new HashMap<>();
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;

            // Parse header
            line = reader.readLine();
            if (line == null || line.isEmpty()) {
                throw new CsvParseException("CSV file is empty");
            }
            lineNumber++;
            headerMap = parseHeader(line);

            // Parse data rows
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    IngestTransactionItem item = parseRow(line, headerMap, lineNumber);
                    transactions.add(item);
                } catch (CsvParseException e) {
                    log.warn("Skipping invalid row {}: {}", lineNumber, e.getMessage());
                    // Continue parsing other rows
                }
            }

            if (transactions.isEmpty()) {
                throw new CsvParseException("No valid transactions found in CSV");
            }

            log.info("Parsed {} transactions from CSV", transactions.size());
            return transactions;

        } catch (CsvParseException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvParseException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Parse the optional {@code category} column into a {@code transaction_id -> category_id}
     * map. Demo-only: real bank-statement uploads never carry a pre-known category (that's what
     * ML inference is for), but curated demo data ships one so seeding can show a realistic
     * category spread even when live inference under/over-predicts a single class.
     */
    public Map<String, Integer> parseCategoryOverrides(InputStream inputStream) throws CsvParseException {
        Map<String, Integer> overrides = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isEmpty()) {
                throw new CsvParseException("CSV file is empty");
            }
            String[] headers = parseCSVLine(headerLine);
            int transactionIdIdx = -1;
            int categoryIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim();
                if (h.equalsIgnoreCase("transaction_id")) {
                    transactionIdIdx = i;
                } else if (h.equalsIgnoreCase("category")) {
                    categoryIdx = i;
                }
            }
            if (transactionIdIdx == -1 || categoryIdx == -1) {
                return overrides; // no category column present — nothing to override
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] values = parseCSVLine(line);
                if (transactionIdIdx >= values.length || categoryIdx >= values.length) {
                    continue;
                }
                String transactionId = values[transactionIdIdx].trim();
                String categoryStr = values[categoryIdx].trim();
                if (transactionId.isEmpty() || categoryStr.isEmpty()) {
                    continue;
                }
                try {
                    overrides.put(transactionId, Integer.parseInt(categoryStr));
                } catch (NumberFormatException e) {
                    log.warn("Skipping non-numeric category '{}' for transaction {}", categoryStr, transactionId);
                }
            }
            return overrides;
        } catch (CsvParseException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvParseException("Failed to parse category overrides: " + e.getMessage(), e);
        }
    }

    /**
     * Parse CSV header row and return column index map.
     */
    private Map<String, Integer> parseHeader(String headerLine) throws CsvParseException {
        Map<String, Integer> map = new HashMap<>();
        String[] headers = parseCSVLine(headerLine);

        String[] requiredFields = {
            "transaction_date", "debit", "credit", "amount", "dr_cr_indicator",
            "transaction_id", "recipient_name", "upi_id", "bank", "transaction_mode",
            "note", "source"
        };

        for (String field : requiredFields) {
            boolean found = false;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(field)) {
                    map.put(field, i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new CsvParseException("Missing required column: " + field);
            }
        }

        return map;
    }

    /**
     * Parse a single CSV data row into an IngestTransactionItem.
     */
    private IngestTransactionItem parseRow(String line, Map<String, Integer> headerMap, int lineNumber)
            throws CsvParseException {
        String[] values = parseCSVLine(line);

        try {
            String transactionDateStr = getField(values, headerMap, "transaction_date");
            String debitStr = getField(values, headerMap, "debit");
            String creditStr = getField(values, headerMap, "credit");
            String amountStr = getField(values, headerMap, "amount");
            String drCrIndicator = getField(values, headerMap, "dr_cr_indicator");
            String transactionId = getField(values, headerMap, "transaction_id");
            String recipientName = getField(values, headerMap, "recipient_name");
            String upiId = getField(values, headerMap, "upi_id");
            String bank = getField(values, headerMap, "bank");
            String transactionMode = getField(values, headerMap, "transaction_mode");
            String note = getField(values, headerMap, "note");

            // Validate required fields
            if (transactionDateStr == null || transactionDateStr.isEmpty()) {
                throw new CsvParseException("transaction_date is required");
            }
            if (transactionId == null || transactionId.isEmpty()) {
                throw new CsvParseException("transaction_id is required");
            }
            if (drCrIndicator == null || drCrIndicator.isEmpty()) {
                throw new CsvParseException("dr_cr_indicator is required");
            }

            // Parse amounts
            BigDecimal debit = debitStr != null && !debitStr.isEmpty() ? new BigDecimal(debitStr) : BigDecimal.ZERO;
            BigDecimal credit = creditStr != null && !creditStr.isEmpty() ? new BigDecimal(creditStr) : BigDecimal.ZERO;
            BigDecimal amount = amountStr != null && !amountStr.isEmpty() ? new BigDecimal(amountStr) : BigDecimal.ZERO;

            // Parse timestamp
            Instant transactionDate;
            try {
                // Try ISO 8601 format (e.g., "2025-07-01T14:01:00Z")
                transactionDate = ISO_FORMATTER.parse(transactionDateStr, Instant::from);
            } catch (Exception e) {
                throw new CsvParseException("Invalid transaction_date format (expected ISO 8601): " + transactionDateStr);
            }

            // Normalize empty strings to null
            recipientName = nullIfEmpty(recipientName);
            upiId = nullIfEmpty(upiId);
            bank = nullIfEmpty(bank);
            transactionMode = nullIfEmpty(transactionMode);
            note = nullIfEmpty(note);

            // Source is always forced to 'bank_statement' by ingest endpoint
            String source = "bank_statement";

            return new IngestTransactionItem(
                transactionDate,
                debit,
                credit,
                amount,
                null, // balance is optional and rarely populated from CSV
                drCrIndicator,
                transactionId,
                recipientName,
                upiId,
                bank,
                transactionMode,
                note,
                source
            );

        } catch (NumberFormatException e) {
            throw new CsvParseException("Invalid numeric value in row " + lineNumber + ": " + e.getMessage());
        } catch (CsvParseException e) {
            throw new CsvParseException("Row " + lineNumber + ": " + e.getMessage());
        }
    }

    /**
     * Parse a CSV line, handling quoted fields.
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    /**
     * Get field value by header name, with bounds checking.
     */
    private String getField(String[] values, Map<String, Integer> headerMap, String fieldName) {
        Integer index = headerMap.get(fieldName);
        if (index == null || index >= values.length) {
            return null;
        }
        return values[index].trim();
    }

    /**
     * Convert empty string to null.
     */
    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Exception thrown during CSV parsing.
     */
    public static class CsvParseException extends Exception {
        public CsvParseException(String message) {
            super(message);
        }

        public CsvParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
