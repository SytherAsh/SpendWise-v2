package com.spendwise.admin;

import java.time.Instant;
import java.util.UUID;

/** A single user's identity fields, read via the {@code spendwise_jobs} (BYPASSRLS) role — Admin may look up any user by id, not just the caller's own. */
public record AdminUserCore(UUID id, String phone, String email, String googleId, Instant createdAt) {}
