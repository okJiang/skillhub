package com.iflytek.skillhub.domain.review;

import java.util.List;
import java.util.Optional;

public interface ReviewCommentThreadRepository {
    ReviewCommentThread save(ReviewCommentThread reviewCommentThread);

    Optional<ReviewCommentThread> findById(Long id);

    List<ReviewCommentThread> findByReviewTaskIdAndSkillVersionIdAndFilePathOrderByLineNumberAscCreatedAtAsc(
            Long reviewTaskId,
            Long skillVersionId,
            String filePath
    );
}
