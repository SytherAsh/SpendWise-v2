package com.spendwise.common.schedule;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class JobScheduleNotFoundException extends ApiException {

    public JobScheduleNotFoundException(String jobKey) {
        super("JOB_SCHEDULE_NOT_FOUND", HttpStatus.NOT_FOUND, "No schedule found for job '" + jobKey + "'");
    }
}
