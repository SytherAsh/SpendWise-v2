package com.spendwise.alerts;

/** Thin wrapper over Firebase Cloud Messaging (E5-S3-T1) — kept as an interface so {@link AlertDispatchServiceImpl} can be unit-tested with a mock, per docs/testing.md. */
public interface FcmClient {

    /** @throws RuntimeException on any send failure — callers must catch and degrade gracefully, never propagate. */
    void send(String fcmToken, String title, String body);
}
