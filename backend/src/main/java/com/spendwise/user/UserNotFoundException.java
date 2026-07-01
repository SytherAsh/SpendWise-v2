package com.spendwise.user;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found");
    }
}
