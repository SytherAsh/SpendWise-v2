package com.spendwise.user;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown for a nonexistent contact id, and equally for another user's contact id (the caller
 * never learns which — same 404-not-403 rationale as TransactionNotFoundException).
 */
public class ContactNotFoundException extends ApiException {

    public ContactNotFoundException() {
        super("CONTACT_NOT_FOUND", HttpStatus.NOT_FOUND, "Contact not found");
    }
}
