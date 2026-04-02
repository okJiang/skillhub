package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewCommentResponse(
        Long id,
        Long threadId,
        String body,
        String createdBy,
        Instant createdAt
) {
}
