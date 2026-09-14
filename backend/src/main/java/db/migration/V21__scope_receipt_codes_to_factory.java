package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Replace column-based uniqueness, not guessed Hibernate/Flyway constraint names. */
public class V21__scope_receipt_codes_to_factory extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return 2026091121;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        JdbcSchema schema = new JdbcSchema(context.getConnection());
        List<JdbcSchema.Table> tables = Arrays.asList(schema.table("inbound_receipts"), schema.table("outbound_receipts"));
        // Fail before changing either table when a legacy schema is structurally incompatible.
        for (JdbcSchema.Table table : tables) {
            schema.column(table, "factory_id", true);
            schema.column(table, "code", true);
            if (JdbcSchema.sameColumns(schema.primaryKeyColumns(table), "code")) {
                throw new SQLException("Cannot replace a code primary key automatically: " + table.name);
            }
        }
        for (JdbcSchema.Table table : tables) migrateTable(schema, table);
    }

    private void migrateTable(JdbcSchema schema, JdbcSchema.Table table) throws SQLException {
        List<JdbcSchema.Index> indexes = schema.indexes(table);
        List<JdbcSchema.Index> globalIndexes = codeIndexes(indexes);
        boolean scoped = hasScopedIndex(indexes);
        if (scoped && globalIndexes.isEmpty()) return;
        String factoryColumn = schema.quote(schema.column(table, "factory_id", true).name);
        String codeColumn = schema.quote(schema.column(table, "code", true).name);
        String desiredName = "uk_" + table.name.toLowerCase(Locale.ROOT) + "_code";

        if (schema.mysql) {
            // One ALTER per table: MySQL never commits the DROP without the replacement ADD.
            schema.execute(mysqlAlter(schema, table, indexes, globalIndexes, scoped, desiredName,
                    factoryColumn, codeColumn));
        } else {
            if (!scoped) {
                Set<String> usedNames = h2Names(schema, table);
                String name = availableName(desiredName, usedNames);
                schema.execute("ALTER TABLE " + schema.qualified(table) + " ADD CONSTRAINT " + schema.quote(name)
                        + " UNIQUE (" + factoryColumn + ", " + codeColumn + ")");
            }
            // H2 cannot combine these clauses. Establish protection first, then remove old
            // constraints; a failed DROP leaves both constraints and can be safely retried.
            List<JdbcSchema.Index> remaining;
            while (!(remaining = codeIndexes(schema.indexes(table))).isEmpty()) {
                JdbcSchema.Index index = remaining.get(0);
                List<String> constraints = h2ConstraintsForIndex(schema, table, index.name);
                if (!constraints.isEmpty()) {
                    // H2 may back several UNIQUE constraints with the same index.
                    for (String constraint : constraints) {
                        schema.execute("ALTER TABLE " + schema.qualified(table) + " DROP CONSTRAINT " + schema.quote(constraint));
                    }
                } else {
                    schema.execute("DROP INDEX " + (table.schema == null ? "" : schema.quote(table.schema) + ".")
                            + schema.quote(index.name));
                }
            }
        }
        List<JdbcSchema.Index> after = schema.indexes(table);
        if (!hasScopedIndex(after) || !codeIndexes(after).isEmpty()) {
            throw new SQLException("Receipt uniqueness migration did not reach its required state: " + table.name);
        }
    }

    String mysqlAlter(JdbcSchema schema, JdbcSchema.Table table, List<JdbcSchema.Index> indexes,
                      List<JdbcSchema.Index> globalIndexes, boolean scoped, String desiredName,
                      String factoryColumn, String codeColumn) {
        List<String> clauses = new ArrayList<>();
        Set<String> occupied = new HashSet<>();
        for (JdbcSchema.Index index : indexes) occupied.add(index.name.toLowerCase(Locale.ROOT));
        for (JdbcSchema.Index index : globalIndexes) {
            clauses.add("DROP INDEX " + schema.quote(index.name));
            occupied.remove(index.name.toLowerCase(Locale.ROOT));
        }
        if (!scoped) {
            clauses.add("ADD CONSTRAINT " + schema.quote(availableName(desiredName, occupied))
                    + " UNIQUE (" + factoryColumn + ", " + codeColumn + ")");
        }
        return "ALTER TABLE " + schema.qualified(table) + " " + String.join(", ", clauses);
    }

    private String availableName(String desired, Set<String> occupied) {
        String candidate = desired;
        int suffix = 1;
        while (occupied.contains(candidate.toLowerCase(Locale.ROOT))) candidate = desired + "_factory_" + suffix++;
        return candidate;
    }

    private List<JdbcSchema.Index> codeIndexes(List<JdbcSchema.Index> indexes) {
        List<JdbcSchema.Index> result = new ArrayList<>();
        for (JdbcSchema.Index index : indexes) if (index.matches("code")) result.add(index);
        return result;
    }

    private boolean hasScopedIndex(List<JdbcSchema.Index> indexes) {
        for (JdbcSchema.Index index : indexes) if (index.matches("factory_id", "code")) return true;
        return false;
    }

    private List<String> h2ConstraintsForIndex(JdbcSchema schema, JdbcSchema.Table table, String indexName) throws SQLException {
        String sql = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE' AND INDEX_NAME = ?";
        try (PreparedStatement statement = schema.connection.prepareStatement(sql)) {
            statement.setString(1, table.schema);
            statement.setString(2, table.name);
            statement.setString(3, indexName);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> constraints = new ArrayList<>();
                while (rows.next()) constraints.add(rows.getString(1));
                return constraints;
            }
        }
    }

    private Set<String> h2Names(JdbcSchema schema, JdbcSchema.Table table) throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = ? "
                + "UNION SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_SCHEMA = ?";
        try (PreparedStatement statement = schema.connection.prepareStatement(sql)) {
            statement.setString(1, table.schema);
            statement.setString(2, table.schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) names.add(rows.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
