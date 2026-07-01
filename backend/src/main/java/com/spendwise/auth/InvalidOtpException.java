package com.spendwise.auth;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code /auth/otp/verify} specifically when the supplied OTP session (Firebase ID
 * token) is expired or invalid — per E1-S1-T3 DoD this endpoint maps that case to 400, distinct
 * from the generic 401 {@link InvalidFirebaseTokenException} used elsewhere (e.g. Google login).
 */
public class InvalidOtpException extends ApiException {

    public InvalidOtpException(String message, Throwable cause) {
        super("INVALID_OTP", HttpStatus.BAD_REQUEST, message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
