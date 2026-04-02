package com.iflytek.skillhub.domain.review;

import java.util.Collection;
import java.util.List;

public interface ReviewTestRunRepository {
    ReviewTestRun save(ReviewTestRun reviewTestRun);

    List<ReviewTestRun> findByReviewTaskIdAndSkillVersionIdOrderByCreatedAtDesc(Long reviewTaskId, Long skillVersionId);

    void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds);
}
