package com.spendwise.alerts;

/** Thin wrapper over SMTP (E5-S3-T2) — kept as an interface so {@link AlertDispatchServiceImpl} can be unit-tested with a mock. */
public interface MailClient {

    /** @throws RuntimeException on any send failure — callers must catch and degrade gracefully, never propagate. */
    void send(String toEmail, String subject, String body);
}
