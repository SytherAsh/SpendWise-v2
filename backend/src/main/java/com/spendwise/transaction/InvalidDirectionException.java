package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** {@code GET /transactions}'s {@code direction} query param is neither {@code "credit"} nor {@code "debit"}. */
public class InvalidDirectionException extends ApiException {

    public InvalidDirectionException(String value) {
        super("INVALID_DIRECTION", HttpStatus.BAD_REQUEST, "direction must be \"credit\" or \"debit\", got: " + value);
    }
}
