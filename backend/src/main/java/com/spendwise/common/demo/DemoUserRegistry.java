package com.spendwise.common.demo;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Narrow, deliberate exception to "cross-module calls go through injected service interfaces
 * only" (CLAUDE.md): the demo account's "current month" is pinned to a fixed calendar month
 * (see {@code demo.frozen-month} in application.yml and its one reader, BudgetServiceImpl)
 * rather than the real clock, so the seeded — static, never re-uploaded — demo transactions
 * always show non-blank budget progress regardless of when the demo is viewed.
 *
 * <p>This holder is the single narrow channel for "is this the demo user" — it carries no
 * business logic and references no other module's domain types, specifically so Budget doesn't
 * need to depend on User or Ingest's service interfaces just to answer one boolean question.
 * Registered once by {@code DemoDataSeeder} on every application startup (even when seeding
 * itself is skipped because the demo user already exists).
 */
@Component
public class DemoUserRegistry {

    private volatile UUID demoUserId;

    public void register(UUID userId) {
        this.demoUserId = userId;
    }

    public boolean isDemoUser(UUID userId) {
        return userId != null && userId.equals(demoUserId);
    }
}
