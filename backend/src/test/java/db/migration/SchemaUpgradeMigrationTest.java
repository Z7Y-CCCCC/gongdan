package db.migration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaUpgradeMigrationTest {

    private final V20__add_product_version productMigration = new V20__add_product_version();
    private final V21__scope_receipt_codes_to_factory receiptMigration = new V21__scope_receipt_codes_to_factory();

    @Test
    void flywayUpgradesCanonicalV19AndDoesNotRepeatAppliedMigrations() throws Exception {
        String url = url(true);
        Flyway previous = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("19").load();
        assertThat(previous.migrate().migrationsExecuted).isEqualTo(19);
        // This fixture specifically verifies V20/V21; the full migration suite covers subsequent versions.
        Flyway current = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("21").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(current.migrate().migrationsExecuted).isZero();
        current.validate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertVersionColumn(connection);
            assertScopedIndexes(connection);
        }
    }

    @Test
    void explicitV19BaselineAcceptsHibernateNamesAndPreexistingVersionColumn() throws Exception {
        String url = url(false);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createLegacySchema(connection, "BIGINT NOT NULL", "CONSTRAINT UK_RANDOM_8173 UNIQUE(code)");
            execute(connection, "INSERT INTO products(id, version) VALUES(1, 17)");
            Flyway flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("19").target("21").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(number(connection, "SELECT version FROM products WHERE id=1")).isEqualTo(17);
            assertVersionColumn(connection);
            assertTenantUniqueness(connection);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }

    @Test
    void missingVersionIsBackfilledAndReexecutionIsANoOp() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "CREATE TABLE products(id BIGINT PRIMARY KEY)");
            execute(connection, "INSERT INTO products(id) VALUES(1)");
            productMigration.migrate(context(connection));
            productMigration.migrate(context(connection));
            assertThat(number(connection, "SELECT version FROM products WHERE id=1")).isZero();
            assertVersionColumn(connection);
        }
    }

    @Test
    void nullableIntegerVersionIsWidenedWithoutResettingExistingCounters() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "CREATE TABLE products(id BIGINT PRIMARY KEY, version INTEGER)");
            execute(connection, "INSERT INTO products(id, version) VALUES(1, NULL), (2, 31)");
            productMigration.migrate(context(connection));
            productMigration.migrate(context(connection));
            assertThat(number(connection, "SELECT version FROM products WHERE id=1")).isZero();
            assertThat(number(connection, "SELECT version FROM products WHERE id=2")).isEqualTo(31);
            assertVersionColumn(connection);
            assertThatThrownBy(() -> execute(connection, "INSERT INTO products(id, version) VALUES(3, NULL)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void incompatibleVersionTypeFailsWithoutCoercingOrDeletingData() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "CREATE TABLE products(id BIGINT PRIMARY KEY, version VARCHAR(32))");
            execute(connection, "INSERT INTO products(id, version) VALUES(1, 'do-not-coerce')");
            assertThatThrownBy(() -> productMigration.migrate(context(connection))).isInstanceOf(SQLException.class);
            assertThat(number(connection, "SELECT COUNT(*) FROM products WHERE version='do-not-coerce'")).isEqualTo(1);
        }
    }

    @Test
    void allGlobalConstraintsIncludingSharedH2IndexesAreRemoved() throws Exception {
        try (Connection connection = connect()) {
            createLegacySchema(connection, null, "CONSTRAINT UK_ONE UNIQUE(code), CONSTRAINT UK_TWO UNIQUE(code)");
            execute(connection, "CREATE UNIQUE INDEX UK_EXTRA ON inbound_receipts(code)");
            receiptMigration.migrate(context(connection));
            receiptMigration.migrate(context(connection));
            assertTenantUniqueness(connection);
        }
    }

    @Test
    void existingScopedIndexesAndUnrelatedUniqueConstraintsArePreserved() throws Exception {
        try (Connection connection = connect()) {
            createLegacySchema(connection, null, "CONSTRAINT UK_SCOPED UNIQUE(code, factory_id)");
            execute(connection, "CREATE UNIQUE INDEX uk_inbound_receipts_code ON inbound_receipts(external_ref)");
            execute(connection, "CREATE UNIQUE INDEX UK_OLD ON inbound_receipts(code)");
            receiptMigration.migrate(context(connection));
            receiptMigration.migrate(context(connection));
            assertTenantUniqueness(connection);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO inbound_receipts(id, factory_id, code, external_ref) VALUES(99, 99, 'OTHER', 'IN-1')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void nameCollisionNeverDropsAnUnrelatedIndex() throws Exception {
        try (Connection connection = connect()) {
            createLegacySchema(connection, null, "CONSTRAINT UK_GLOBAL UNIQUE(code)");
            execute(connection, "CREATE UNIQUE INDEX uk_inbound_receipts_code ON inbound_receipts(external_ref)");
            receiptMigration.migrate(context(connection));
            assertTenantUniqueness(connection);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO inbound_receipts(id, factory_id, code, external_ref) VALUES(99, 99, 'OTHER', 'IN-1')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rollbackAfterFailedDropKeepsUniquenessAndCanBeRetried() throws Exception {
        try (Connection connection = connect()) {
            createLegacySchema(connection, null, "CONSTRAINT UK_LEGACY UNIQUE(code)");
            connection.setAutoCommit(false);
            Connection failing = failOnce(connection, sql -> sql.contains("DROP CONSTRAINT"));
            assertThatThrownBy(() -> receiptMigration.migrate(context(failing))).isInstanceOf(SQLException.class);
            connection.rollback();
            JdbcSchema schema = new JdbcSchema(connection);
            List<JdbcSchema.Index> indexes = schema.indexes(schema.table("inbound_receipts"));
            assertThat(indexes.stream().anyMatch(index -> index.matches("code"))).isTrue();
            assertThat(indexes.stream().anyMatch(index -> index.matches("factory_id", "code"))).isTrue();
            receiptMigration.migrate(context(connection));
            assertTenantUniqueness(connection);
        }
    }

    @Test
    void partiallyCompletedFirstTableCanBeRetriedAfterSecondTableFails() throws Exception {
        try (Connection connection = connect()) {
            createLegacySchema(connection, null, "CONSTRAINT UK_LEGACY UNIQUE(code)");
            connection.setAutoCommit(false);
            Connection failing = failOnce(connection,
                    sql -> sql.contains("outbound_receipts") && sql.contains("ADD CONSTRAINT"));
            assertThatThrownBy(() -> receiptMigration.migrate(context(failing))).isInstanceOf(SQLException.class);
            connection.rollback();
            receiptMigration.migrate(context(connection));
            receiptMigration.migrate(context(connection));
            assertTenantUniqueness(connection);
        }
    }

    @Test
    void missingRequiredColumnFailsBeforeChangingTheOtherTable() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "CREATE TABLE inbound_receipts(id BIGINT PRIMARY KEY, factory_id BIGINT, code VARCHAR(64) UNIQUE)");
            execute(connection, "CREATE TABLE outbound_receipts(id BIGINT PRIMARY KEY, code VARCHAR(64) UNIQUE)");
            assertThatThrownBy(() -> receiptMigration.migrate(context(connection)))
                    .isInstanceOf(SQLException.class).hasMessageContaining("factory_id");
            JdbcSchema schema = new JdbcSchema(connection);
            assertThat(schema.indexes(schema.table("inbound_receipts")).stream().anyMatch(index -> index.matches("code"))).isTrue();
        }
    }

    @Test
    void mysqlReplacementIsOneAlterWithAllDropsAndTheReplacementAdd() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        JdbcSchema schema = new JdbcSchema(connection);
        JdbcSchema.Table table = new JdbcSchema.Table("workorder", null, "inbound_receipts");
        JdbcSchema.Index canonical = index("uk_inbound_receipts_code", true, "code");
        JdbcSchema.Index legacy = index("UK_legacy`name", true, "code");
        JdbcSchema.Index unrelated = index("UK_external", true, "external_ref");
        String sql = receiptMigration.mysqlAlter(schema, table, Arrays.asList(canonical, legacy, unrelated),
                Arrays.asList(canonical, legacy), false, "uk_inbound_receipts_code", "`factory_id`", "`code`");
        assertThat(sql).isEqualTo("ALTER TABLE `workorder`.`inbound_receipts` "
                + "DROP INDEX `uk_inbound_receipts_code`, DROP INDEX `UK_legacy``name`, "
                + "ADD CONSTRAINT `uk_inbound_receipts_code` UNIQUE (`factory_id`, `code`)");
        assertThat(sql).doesNotContain(";", "DROP INDEX `UK_external`");
    }

    @Test
    void mysqlExistingScopedIndexIsPreservedAndNameCollisionsGetAnotherName() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        JdbcSchema schema = new JdbcSchema(connection);
        JdbcSchema.Table table = new JdbcSchema.Table("workorder", null, "outbound_receipts");
        JdbcSchema.Index unrelated = index("uk_outbound_receipts_code", true, "external_ref");
        JdbcSchema.Index global = index("UK_global", true, "code");
        String sql = receiptMigration.mysqlAlter(schema, table, Arrays.asList(global, unrelated),
                Arrays.asList(global), false, "uk_outbound_receipts_code", "`factory_id`", "`code`");
        assertThat(sql).contains("ADD CONSTRAINT `uk_outbound_receipts_code_factory_1`")
                .doesNotContain("DROP INDEX `uk_outbound_receipts_code`");
        String alreadyScoped = receiptMigration.mysqlAlter(schema, table, Arrays.asList(global, unrelated),
                Arrays.asList(global), true, "uk_outbound_receipts_code", "`factory_id`", "`code`");
        assertThat(alreadyScoped).isEqualTo("ALTER TABLE `workorder`.`outbound_receipts` DROP INDEX `UK_global`");
    }

    private void createLegacySchema(Connection connection, String versionType, String constraint) throws SQLException {
        execute(connection, "CREATE TABLE products(id BIGINT PRIMARY KEY"
                + (versionType == null ? "" : ", version " + versionType) + ")");
        for (String table : Arrays.asList("inbound_receipts", "outbound_receipts")) {
            // H2 constraint names are schema-wide; Hibernate also uses different hashes per table.
            String scopedConstraintNames = constraint.replace("UK_", "UK_" + table + "_");
            execute(connection, "CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, factory_id BIGINT NOT NULL, "
                    + "code VARCHAR(64) NOT NULL, external_ref VARCHAR(64), " + scopedConstraintNames + ")");
        }
    }

    private void assertTenantUniqueness(Connection connection) throws SQLException {
        assertScopedIndexes(connection);
        for (String table : Arrays.asList("inbound_receipts", "outbound_receipts")) {
            String prefix = table.startsWith("inbound") ? "IN" : "OUT";
            execute(connection, "INSERT INTO " + table + "(id, factory_id, code, external_ref) VALUES"
                    + "(1, 1, 'SAME', '" + prefix + "-1'), (2, 2, 'SAME', '" + prefix + "-2')");
            assertThatThrownBy(() -> execute(connection, "INSERT INTO " + table
                    + "(id, factory_id, code, external_ref) VALUES(3, 1, 'SAME', 'THIRD')"))
                    .isInstanceOf(SQLException.class);
            assertThat(number(connection, "SELECT COUNT(*) FROM " + table)).isEqualTo(2);
        }
    }

    private void assertScopedIndexes(Connection connection) throws SQLException {
        JdbcSchema schema = new JdbcSchema(connection);
        for (String name : Arrays.asList("inbound_receipts", "outbound_receipts")) {
            List<JdbcSchema.Index> indexes = schema.indexes(schema.table(name));
            assertThat(indexes.stream().anyMatch(index -> index.matches("factory_id", "code"))).isTrue();
            assertThat(indexes.stream().anyMatch(index -> index.matches("code"))).isFalse();
        }
    }

    private void assertVersionColumn(Connection connection) throws SQLException {
        JdbcSchema schema = new JdbcSchema(connection);
        JdbcSchema.Column column = schema.column(schema.table("products"), "version", true);
        assertThat(column.type).isEqualTo(Types.BIGINT);
        assertThat(column.nullable).isFalse();
        assertThat(column.defaultValue).isEqualTo("0");
    }

    private JdbcSchema.Index index(String name, boolean unique, String... columns) {
        JdbcSchema.Index index = new JdbcSchema.Index(name, unique);
        for (int i = 0; i < columns.length; i++) index.columns.put(i + 1, columns[i]);
        return index;
    }

    private String url(boolean lowercase) {
        return "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
                + (lowercase ? ";DATABASE_TO_LOWER=TRUE" : "");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url(true), "sa", "");
    }

    private Context context(Connection connection) {
        return new Context() {
            @Override public Configuration getConfiguration() { return null; }
            @Override public Connection getConnection() { return connection; }
        };
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long number(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private Connection failOnce(Connection delegate, Predicate<String> predicate) {
        AtomicBoolean failed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if (!"createStatement".equals(method.getName())) return result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                            (statementProxy, statementMethod, statementArgs) -> {
                                if ("execute".equals(statementMethod.getName()) && statementArgs[0] instanceof String
                                        && predicate.test((String) statementArgs[0]) && failed.compareAndSet(false, true)) {
                                    throw new SQLException("Simulated DDL failure");
                                }
                                return invoke(result, statementMethod, statementArgs);
                            });
                });
    }

    private Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
