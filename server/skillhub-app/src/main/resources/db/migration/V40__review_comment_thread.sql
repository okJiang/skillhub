CREATE TABLE review_comment_thread (
    id BIGSERIAL PRIMARY KEY,
    review_task_id BIGINT NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    file_path VARCHAR(1024) NOT NULL,
    line_number INT NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_comment_thread_review_file_line
    ON review_comment_thread (review_task_id, skill_version_id, file_path, line_number, created_at);

CREATE TABLE review_comment (
    id BIGSERIAL PRIMARY KEY,
    thread_id BIGINT NOT NULL REFERENCES review_comment_thread(id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_comment_thread_created_at
    ON review_comment (thread_id, created_at);
