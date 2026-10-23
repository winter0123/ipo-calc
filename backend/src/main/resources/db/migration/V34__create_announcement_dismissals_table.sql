CREATE TABLE announcement_dismissals (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    dismissed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, announcement_id)
);
CREATE INDEX idx_announcement_dismissals_announcement_id ON announcement_dismissals (announcement_id);
