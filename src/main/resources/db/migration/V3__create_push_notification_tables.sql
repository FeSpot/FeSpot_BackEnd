CREATE TABLE IF NOT EXISTS push_tokens (
    push_token_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    expo_push_token VARCHAR(512) NOT NULL,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    PRIMARY KEY (push_token_id),
    CONSTRAINT uk_push_tokens_expo_push_token UNIQUE (expo_push_token),
    CONSTRAINT uk_push_tokens_user_id UNIQUE (user_id),
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE IF NOT EXISTS push_delivery_tickets (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    receipt_id    VARCHAR(255) NOT NULL,
    push_token_id BIGINT       NOT NULL,
    created_at    DATETIME(6)  NULL,
    updated_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_delivery_ticket_receipt_id UNIQUE (receipt_id),
    CONSTRAINT fk_push_delivery_tickets_push_token
        FOREIGN KEY (push_token_id) REFERENCES push_tokens (push_token_id)
);

-- 과거 Hibernate가 push_tokens를 생성한 환경에는 사용자 UNIQUE가 없을 수 있다.
-- 같은 의미의 단일 컬럼 UNIQUE가 없다면 추가하고, 중복 데이터가 있으면 실패시킨다.
SET @has_push_token_user_unique = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'push_tokens'
          AND non_unique = 0
        GROUP BY index_name
        HAVING COUNT(*) = 1
           AND MIN(column_name) = 'user_id'
    ) AS unique_user_indexes
);

SET @add_push_token_user_unique = IF(
    @has_push_token_user_unique = 0,
    'ALTER TABLE push_tokens ADD CONSTRAINT uk_push_tokens_user_id UNIQUE (user_id)',
    'SELECT 1'
);

PREPARE add_push_token_user_unique_statement FROM @add_push_token_user_unique;
EXECUTE add_push_token_user_unique_statement;
DEALLOCATE PREPARE add_push_token_user_unique_statement;
