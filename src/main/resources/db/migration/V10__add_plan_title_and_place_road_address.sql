-- visit_plan: (user_id, festival_id, plan_target_date) 복합 unique 제약 제거
-- 제약이 이름 없이 선언되어 Hibernate가 생성한 이름(UK+해시)을 알 수 없으므로
-- information_schema에서 실제 인덱스 이름을 조회해 동적으로 드랍한다.
SET @index_name := (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'visit_plan'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
      AND column_name = 'plan_target_date'
    LIMIT 1
);

SET @drop_ddl := IF(
    @index_name IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE visit_plan DROP INDEX `', @index_name, '`')
);

PREPARE drop_stmt FROM @drop_ddl;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

-- visit_plan: 계획 제목 추가 (기존 행 호환을 위해 DEFAULT '' 적용)
ALTER TABLE visit_plan
    ADD COLUMN title VARCHAR(20) NOT NULL DEFAULT '';

-- visit_place: 경유지 도로명 주소 추가 (기존 행 호환을 위해 DEFAULT '' 적용)
ALTER TABLE visit_place
    ADD COLUMN road_address VARCHAR(100) NOT NULL DEFAULT '';
