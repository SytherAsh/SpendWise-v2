package com.spendwise.auth.dto;

public record TokenRefreshResponse(String accessToken, String refreshToken, long expiresIn) {}
