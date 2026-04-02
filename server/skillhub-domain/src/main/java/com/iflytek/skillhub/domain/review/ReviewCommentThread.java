package com.iflytek.skillhub.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "review_comment_thread")
public class ReviewCommentThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_task_id", nullable = false)
    private Long reviewTaskId;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewCommentThread() {
    }

    public ReviewCommentThread(Long reviewTaskId,
                               Long skillVersionId,
                               String filePath,
                               Integer lineNumber,
                               String createdBy) {
        this.reviewTaskId = reviewTaskId;
        this.skillVersionId = skillVersionId;
        this.filePath = filePath != null ? filePath.trim() : null;
        this.lineNumber = lineNumber;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
