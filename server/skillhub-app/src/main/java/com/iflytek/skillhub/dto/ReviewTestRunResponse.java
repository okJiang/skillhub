package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewTestRunResponse(
        Long id,
        Long skillVersionId,
        String source,
        String status,
        String name,
        String summary,
        String detailsMarkdown,
        String externalUrl,
        String createdBy,
        Instant createdAt
) {
}
