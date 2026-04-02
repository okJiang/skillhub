package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewCommentThreadRequest(
        @NotBlank(message = "{validation.review.comment.filePath.notBlank}")
        @Size(max = 1024, message = "{validation.review.comment.filePath.size}")
        String filePath,
        @Min(value = 1, message = "{validation.review.comment.lineNumber.min}")
        Integer lineNumber,
        @NotBlank(message = "{validation.review.comment.body.notBlank}")
        @Size(max = 5000, message = "{validation.review.comment.body.size}")
        String body
) {
}
