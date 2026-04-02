package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewCommentRequest(
        @NotBlank(message = "{validation.review.comment.body.notBlank}")
        @Size(max = 5000, message = "{validation.review.comment.body.size}")
        String body
) {
}
