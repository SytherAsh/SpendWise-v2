package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** {@code GET /transactions}'s {@code category} query param is neither a numeric id nor {@code "uncategorized"}. */
public class InvalidCategoryFilterException extends ApiException {

    public InvalidCategoryFilterException(String value) {
        super("INVALID_CATEGORY_FILTER", HttpStatus.BAD_REQUEST, "category must be a numeric id or \"uncategorized\", got: " + value);
    }
}
