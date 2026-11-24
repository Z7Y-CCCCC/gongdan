package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailPageResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Private H2 sessions with explicit DB time zones, independent of the host's default zone. */
class InventoryJdbcTimeMappingTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "+08:00", "America/New_York"})
    void timestampWrittenReceiptTimesUseTheSameMappingForFilteringAndReading(String databaseZone) throws Exception {
        String database = "jdbc:h2:mem:inventory_time_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        LocalDateTime time = LocalDateTime.of(2026, 9, 11, 12, 0);
        try (Connection connection = DriverManager.getConnection(database, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + databaseZone + "'");
            statement.execute("CREATE TABLE units (id BIGINT, factory_id BIGINT, name VARCHAR(64))");
            statement.execute("CREATE TABLE products (id BIGINT, factory_id BIGINT, code VARCHAR(64), name VARCHAR(64),"
                    + " spec VARCHAR(64), attribute VARCHAR(64), unit_id BIGINT)");
            statement.execute("CREATE TABLE inbound_receipts (id BIGINT, factory_id BIGINT, code VARCHAR(64),"
                    + " inbound_type VARCHAR(64), inbound_time TIMESTAMP NOT NULL, remark VARCHAR(64))");
            statement.execute("CREATE TABLE outbound_receipts (id BIGINT, factory_id BIGINT, code VARCHAR(64),"
                    + " outbound_type VARCHAR(64), outbound_time TIMESTAMP NOT NULL, remark VARCHAR(64))");
            statement.execute("CREATE TABLE inbound_receipt_items (id BIGINT, inbound_receipt_id BIGINT,"
                    + " product_id BIGINT, quantity INTEGER)");
            statement.execute("CREATE TABLE outbound_receipt_items (id BIGINT, outbound_receipt_id BIGINT,"
                    + " product_id BIGINT, quantity INTEGER)");
            statement.execute("INSERT INTO units VALUES (1, 1, 'kg'), (2, 2, 'foreign-unit')");
            statement.execute("INSERT INTO products VALUES (1, 1, 'QA', 'Product', NULL, '自制', 1),"
                    + " (2, 2, 'FOREIGN', 'Foreign product', NULL, '自制', 2)");
            receipt(connection, "inbound", 1, 1, "IN_QA", time);
            receipt(connection, "outbound", 2, 1, "OUT_QA", time);
            receipt(connection, "inbound", 3, 2, "IN_FOREIGN", time);
            statement.execute("INSERT INTO inbound_receipt_items VALUES (1, 1, 1, 10), (2, 1, 1, 10), (3, 3, 2, 99)");
            statement.execute("INSERT INTO outbound_receipt_items VALUES (1, 2, 1, 3)");
            InventoryDetailService service = new InventoryDetailService(new NamedParameterJdbcTemplate(
                    new SingleConnectionDataSource(connection, true)));

            InventoryDetailPageResponse first = service.list(1L, null, null, null,
                    null, null, null, null, "自制", time, time, 1, 2);
            assertThat(first.getTotal()).as("Inclusive exact wall-clock boundary in DB zone %s", databaseZone).isEqualTo(3);
            assertThat(first.getItems()).extracting("changeQty").containsExactly(10, 10);
            assertThat(first.getItems()).extracting("operationTime").containsOnly(time);
            assertThat(first.getItems()).extracting("unitName").containsOnly("kg");
            InventoryDetailPageResponse second = service.list(1L, null, null, null,
                    null, null, null, null, null, time, time, 2, 2);
            assertThat(second.getTotal()).isEqualTo(3);
            assertThat(second.getItems()).extracting("changeQty").containsExactly(-3);
            assertThat(second.getItems()).extracting("operationTime").containsExactly(time);
            assertThat(service.list(1L, null, null, null, null, null, null, null, null,
                    time.plusSeconds(1), time.plusSeconds(1), 1, 10).getTotal()).isZero();
        }
    }

    private static void receipt(Connection connection, String direction, int id, int factoryId,
                                String code, LocalDateTime time) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + direction
                + "_receipts VALUES (?, ?, ?, 'NORMAL', ?, NULL)")) {
            insert.setInt(1, id); insert.setInt(2, factoryId); insert.setString(3, code);
            // The JDBC mapping used by Hibernate 5's LocalDateTime descriptor.
            insert.setTimestamp(4, Timestamp.valueOf(time));
            insert.executeUpdate();
        }
    }
}
