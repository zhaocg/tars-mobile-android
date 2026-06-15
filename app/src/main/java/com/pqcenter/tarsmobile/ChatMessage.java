package com.pqcenter.tarsmobile;

import java.util.UUID;

final class ChatMessage {
    final String id;
    final String runId;
    final String role;
    final long createdAtMillis;
    String content;
    String status;
    boolean streaming;

    ChatMessage(String id, String runId, String role, String content, String status, long createdAtMillis, boolean streaming) {
        this.id = id;
        this.runId = runId;
        this.role = role;
        this.content = content;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.streaming = streaming;
    }

    static ChatMessage user(String content) {
        return new ChatMessage(
            "local:" + UUID.randomUUID(),
            null,
            "user",
            content,
            "created",
            System.currentTimeMillis(),
            false
        );
    }

    static ChatMessage assistantStream(String runId) {
        return new ChatMessage(
            "stream:" + runId,
            runId,
            "assistant",
            "",
            "created",
            System.currentTimeMillis(),
            true
        );
    }

    static ChatMessage assistantFinal(String runId, String content) {
        String normalizedRunId = runId == null || runId.trim().isEmpty() ? null : runId;
        return new ChatMessage(
            normalizedRunId == null ? "assistant:" + UUID.randomUUID() : "assistant:" + normalizedRunId,
            normalizedRunId,
            "assistant",
            content,
            "completed",
            System.currentTimeMillis(),
            false
        );
    }

    boolean isFromUser() {
        return "user".equals(role);
    }
}
