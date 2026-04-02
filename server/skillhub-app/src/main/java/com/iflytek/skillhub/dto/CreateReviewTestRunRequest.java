package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.review.ReviewTestRunSource;
import com.iflytek.skillhub.domain.review.ReviewTestRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewTestRunRequest(
        @NotNull(message = "{validation.review.testRun.source.notNull}")
        ReviewTestRunSource source,
        @NotNull(message = "{validation.review.testRun.status.notNull}")
        ReviewTestRunStatus status,
        @NotBlank(message = "{validation.review.testRun.name.notBlank}")
        @Size(max = 200, message = "{validation.review.testRun.name.size}")
        String name,
        @Size(max = 2000, message = "{validation.review.testRun.summary.size}")
        String summary,
        String detailsMarkdown,
        @Size(max = 2048, message = "{validation.review.testRun.externalUrl.size}")
        String externalUrl
) {
}
