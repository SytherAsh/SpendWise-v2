package com.spendwise.auth;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a refresh token is unknown, expired, or already revoked (not a replay). */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException(String message) {
        super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, message);
    }
}
