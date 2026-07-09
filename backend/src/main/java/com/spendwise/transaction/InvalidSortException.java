package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** {@code GET /transactions}'s {@code sort} query param is neither {@code date_desc} nor {@code amount_desc}, or {@code cursor} was combined with {@code sort=amount_desc} (unsupported — amount ranking is a bounded top-N read, not a paginated feed). */
public class InvalidSortException extends ApiException {

    public InvalidSortException(String message) {
        super("INVALID_SORT", HttpStatus.BAD_REQUEST, message);
    }
}
