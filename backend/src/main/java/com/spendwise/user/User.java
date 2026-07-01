package com.spendwise.user;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String phone, String email, String googleId, Instant createdAt) {}
