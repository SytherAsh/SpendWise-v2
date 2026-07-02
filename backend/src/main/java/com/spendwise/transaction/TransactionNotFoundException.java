package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown for a nonexistent transaction id, and equally for another user's transaction id (the
 * caller never learns which — E3-S2-T2: 404, not 403, to avoid leaking existence).
 */
public class TransactionNotFoundException extends ApiException {

    public TransactionNotFoundException() {
        super("TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND, "Transaction not found");
    }
}
