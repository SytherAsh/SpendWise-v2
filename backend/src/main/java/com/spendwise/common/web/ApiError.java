package com.spendwise.common.web;

/** Consistent error response shape mandated by docs/development_guidelines.md API Rules. */
public record ApiError(String error, String message, int status) {}
