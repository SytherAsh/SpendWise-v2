package com.spendwise.auth;

/** Identity resolved from a Firebase ID token after server-side verification. */
public record FirebaseVerifiedIdentity(String uid, String phoneNumber, String email) {}
