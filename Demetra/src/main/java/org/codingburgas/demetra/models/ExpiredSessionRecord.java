package org.codingburgas.demetra.models;

import java.time.Instant;

public record ExpiredSessionRecord(String sessionId, String principalName,
                                   Instant createdAt, Instant lastAccessAt, Instant expiresAt) {
}
