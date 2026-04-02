package com.iflytek.skillhub.domain.review;

import java.util.Collection;
import java.util.List;

public interface ReviewCommentRepository {
    ReviewComment save(ReviewComment reviewComment);

    List<ReviewComment> findByThreadIdInOrderByCreatedAtAsc(Collection<Long> threadIds);
}
