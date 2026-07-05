package com.spendwise.admin;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown by {@code POST /admin/auth/login} when the username/password pair doesn't match the seeded admin credential. */
public class InvalidAdminCredentialsException extends ApiException {

    public InvalidAdminCredentialsException() {
        super("INVALID_ADMIN_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid admin username or password");
    }
}
