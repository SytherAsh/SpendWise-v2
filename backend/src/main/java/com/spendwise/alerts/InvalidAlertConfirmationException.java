package com.spendwise.alerts;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** {@code POST /alerts/:id/confirm} (E6-S2-T2) is only valid for {@code recurring_payment} alerts. */
public class InvalidAlertConfirmationException extends ApiException {

    public InvalidAlertConfirmationException() {
        super("ALERT_NOT_CONFIRMABLE", HttpStatus.BAD_REQUEST, "Only recurring_payment alerts can be confirmed");
    }
}
