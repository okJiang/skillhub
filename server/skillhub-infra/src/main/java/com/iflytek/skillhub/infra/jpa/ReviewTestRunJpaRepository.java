package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.ReviewTestRun;
import com.iflytek.skillhub.domain.review.ReviewTestRunRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewTestRunJpaRepository extends JpaRepository<ReviewTestRun, Long>, ReviewTestRunRepository {

    @Override
    List<ReviewTestRun> findByReviewTaskIdAndSkillVersionIdOrderByCreatedAtDesc(Long reviewTaskId, Long skillVersionId);

    @Override
    void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds);
}
