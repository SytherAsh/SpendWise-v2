package com.spendwise.transaction;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class EmiNotFoundException extends ApiException {

    public EmiNotFoundException() {
        super("EMI_NOT_FOUND", HttpStatus.NOT_FOUND, "EMI not found");
    }
}
