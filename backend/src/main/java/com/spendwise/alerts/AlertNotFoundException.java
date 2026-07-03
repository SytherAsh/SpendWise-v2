package com.spendwise.alerts;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class AlertNotFoundException extends ApiException {

    public AlertNotFoundException() {
        super("ALERT_NOT_FOUND", HttpStatus.NOT_FOUND, "Alert not found");
    }
}
