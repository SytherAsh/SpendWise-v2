package com.spendwise.user;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** A contact was submitted with no identifier at all (no recipientNamePattern, upiId, or phoneNumber). */
public class InvalidContactException extends ApiException {

    public InvalidContactException() {
        super(
                "CONTACT_MISSING_IDENTIFIER",
                HttpStatus.BAD_REQUEST,
                "A contact needs at least one of recipientNamePattern, upiId, or phoneNumber");
    }
}
