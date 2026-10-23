CREATE TABLE announcements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    start_at DATE NOT NULL,
    end_at DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_announcements_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_announcements_dates CHECK (start_at <= end_at)
);
CREATE INDEX idx_announce_status_active ON announcements (status, deleted_at, start_at, end_at);
CREATE INDEX idx_announce_start_at ON announcements (start_at ASC, id ASC);
