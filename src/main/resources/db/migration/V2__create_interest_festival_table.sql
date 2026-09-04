-- 관심 축제 테이블 (InterestFestival 엔티티와 1:1 대응)
CREATE TABLE IF NOT EXISTS interest_festival (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    festival_id BIGINT      NOT NULL,
    created_at  DATETIME(6) NULL,
    updated_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interest_festival UNIQUE (user_id, festival_id),
    CONSTRAINT fk_interest_festival_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_interest_festival_festival FOREIGN KEY (festival_id) REFERENCES festivals (festival_id)
);
