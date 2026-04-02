package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.ReviewCommentThread;
import com.iflytek.skillhub.domain.review.ReviewCommentThreadRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewCommentThreadJpaRepository extends JpaRepository<ReviewCommentThread, Long>, ReviewCommentThreadRepository {

    @Override
    List<ReviewCommentThread> findByReviewTaskIdAndSkillVersionIdAndFilePathOrderByLineNumberAscCreatedAtAsc(
            Long reviewTaskId,
            Long skillVersionId,
            String filePath
    );
}
