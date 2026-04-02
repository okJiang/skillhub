package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.ReviewComment;
import com.iflytek.skillhub.domain.review.ReviewCommentRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewCommentJpaRepository extends JpaRepository<ReviewComment, Long>, ReviewCommentRepository {

    @Override
    List<ReviewComment> findByThreadIdInOrderByCreatedAtAsc(Collection<Long> threadIds);
}
