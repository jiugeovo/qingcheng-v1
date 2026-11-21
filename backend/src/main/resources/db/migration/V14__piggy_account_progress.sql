CREATE TABLE piggy_progress (
    user_id BIGINT NOT NULL PRIMARY KEY,
    tap_count BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    legacy_import_closed BOOLEAN NOT NULL,
    CONSTRAINT fk_piggy_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE piggy_device_progress (
    user_id BIGINT NOT NULL,
    device_id CHAR(36) NOT NULL,
    submitted_taps BIGINT NOT NULL,
    PRIMARY KEY (user_id, device_id),
    CONSTRAINT fk_piggy_device_owner FOREIGN KEY (user_id) REFERENCES piggy_progress(user_id) ON DELETE CASCADE
);
