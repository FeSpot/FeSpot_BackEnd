package com.api.fespot.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ReportLocationMigrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void allowsUnnamedLocationsAfterV8AndPreservesExistingNames() throws Exception {
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            // V5까지 적용된 DB의 제보 관련 테이블에서 이번 변경에 필요한 부분만 준비한다.
            statement.execute("CREATE TABLE report_locations (report_location_id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE reports (report_id BIGINT PRIMARY KEY)");

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .baselineOnMigrate(true)
                    .baselineVersion("5")
                    .target("7")
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            try (var column = statement.executeQuery("SHOW COLUMNS FROM report_locations LIKE 'signature_name'")) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("Type")).isEqualTo("varchar(255)");
                assertThat(column.getString("Null")).isEqualTo("NO");
            }
            try (var column = statement.executeQuery("SHOW COLUMNS FROM reports LIKE 'original_location'")) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("Type")).isEqualTo("varchar(100)");
                assertThat(column.getString("Null")).isEqualTo("NO");
            }
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
            statement.execute("INSERT INTO report_locations VALUES (1, '메인무대')");

            flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .target("8")
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (var column = statement.executeQuery("SHOW COLUMNS FROM report_locations LIKE 'signature_name'")) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("Type")).isEqualTo("varchar(255)");
                assertThat(column.getString("Null")).isEqualTo("YES");
            }
            statement.execute("INSERT INTO report_locations VALUES (2, NULL)");
            try (var rows = statement.executeQuery("SELECT signature_name FROM report_locations ORDER BY report_location_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("signature_name")).isEqualTo("메인무대");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("signature_name")).isNull();
            }
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
