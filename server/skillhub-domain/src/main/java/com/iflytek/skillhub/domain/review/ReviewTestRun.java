package com.iflytek.skillhub.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "review_test_run")
public class ReviewTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_task_id", nullable = false)
    private Long reviewTaskId;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewTestRunSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewTestRunStatus status;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "details_markdown", columnDefinition = "TEXT")
    private String detailsMarkdown;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewTestRun() {
    }

    public ReviewTestRun(Long reviewTaskId,
                         Long skillVersionId,
                         ReviewTestRunSource source,
                         ReviewTestRunStatus status,
                         String name,
                         String summary,
                         String detailsMarkdown,
                         String externalUrl,
                         String createdBy) {
        this.reviewTaskId = reviewTaskId;
        this.skillVersionId = skillVersionId;
        this.source = source;
        this.status = status;
        this.name = name != null ? name.trim() : null;
        this.summary = normalizeOptionalText(summary);
        this.detailsMarkdown = normalizeOptionalText(detailsMarkdown);
        this.externalUrl = normalizeOptionalText(externalUrl);
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

    public ReviewTestRunSource getSource() {
        return source;
    }

    public ReviewTestRunStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetailsMarkdown() {
        return detailsMarkdown;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
