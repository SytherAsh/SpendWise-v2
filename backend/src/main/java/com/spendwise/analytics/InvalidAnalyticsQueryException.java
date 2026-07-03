package com.spendwise.analytics;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Bad/missing {@code from}/{@code to}, or an invalid {@code granularity} — always 400. */
public class InvalidAnalyticsQueryException extends ApiException {

    public InvalidAnalyticsQueryException(String message) {
        super("INVALID_ANALYTICS_QUERY", HttpStatus.BAD_REQUEST, message);
    }
}
