CREATE TABLE review_test_run (
    id BIGSERIAL PRIMARY KEY,
    review_task_id BIGINT NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary TEXT,
    details_markdown TEXT,
    external_url VARCHAR(2048),
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_test_run_review_version_created_at
    ON review_test_run (review_task_id, skill_version_id, created_at DESC);
