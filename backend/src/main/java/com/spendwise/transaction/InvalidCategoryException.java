package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** docs/api.md: {@code PUT /transactions/:id/category} returns 400 (not 404) for an unknown {@code category_id}. */
public class InvalidCategoryException extends ApiException {

    public InvalidCategoryException(int categoryId) {
        super("CATEGORY_NOT_FOUND", HttpStatus.BAD_REQUEST, "Category with id " + categoryId + " does not exist");
    }
}
