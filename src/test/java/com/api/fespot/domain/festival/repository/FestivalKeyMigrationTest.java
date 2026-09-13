package com.api.fespot.domain.festival.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FestivalKeyMigrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void backfillsOnlyTheTwoSpecifiedFestivalsIn2026() throws Exception {
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE festivals (festival_id BIGINT PRIMARY KEY, contents_id VARCHAR(64) NOT NULL, year INT NOT NULL)");
            statement.execute("INSERT INTO festivals VALUES (1, '142080', 2026), (2, '140799', 2026), (3, '142080', 2025), (4, '999999', 2026)");

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .baselineOnMigrate(true)
                    .baselineVersion("8")
                    .target("9")
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            List<String> keys = new ArrayList<>();
            try (var rows = statement.executeQuery("SELECT festival_key FROM festivals ORDER BY festival_id")) {
                while (rows.next()) {
                    keys.add(rows.getString("festival_key"));
                }
            }
            assertThat(keys).containsExactly(
                    "BUSAN_SEA_FESTIVAL", "BUSAN_INTERNATIONAL_ROCK_FESTIVAL", null, null);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
