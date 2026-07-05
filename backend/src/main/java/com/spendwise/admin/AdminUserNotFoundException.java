package com.spendwise.admin;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class AdminUserNotFoundException extends ApiException {

    public AdminUserNotFoundException() {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found");
    }
}
