package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record ReviewCommentThreadResponse(
        Long id,
        Long reviewTaskId,
        Long skillVersionId,
        String filePath,
        Integer lineNumber,
        String createdBy,
        Instant createdAt,
        List<ReviewCommentResponse> comments
) {
}
