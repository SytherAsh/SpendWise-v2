package com.spendwise.common.schedule;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidJobScheduleException extends ApiException {

    public InvalidJobScheduleException(String message) {
        super("INVALID_JOB_SCHEDULE", HttpStatus.BAD_REQUEST, message);
    }
}
