package com.spendwise.chatbot;

/** Mirrors the {@code chat_role} Postgres enum (docs/database.md `chatbot_conversations` table). */
public enum ChatRole {
    USER,
    ASSISTANT;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static ChatRole fromDbValue(String value) {
        return ChatRole.valueOf(value.toUpperCase());
    }
}
