package com.spendwise.auth;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a Bearer token fails validation against {@code JWT_SECRET}. */
public class InvalidUserJwtException extends ApiException {

    public InvalidUserJwtException(String message, Throwable cause) {
        super("INVALID_ACCESS_TOKEN", HttpStatus.UNAUTHORIZED, message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
