package com.spendwise.auth;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a Firebase ID token is invalid, expired, or malformed. */
public class InvalidFirebaseTokenException extends ApiException {

    public InvalidFirebaseTokenException(String message, Throwable cause) {
        super("INVALID_FIREBASE_TOKEN", HttpStatus.UNAUTHORIZED, message);
        if (cause != null) {
            initCause(cause);
        }
    }

    public InvalidFirebaseTokenException(String message) {
        this(message, null);
    }
}
