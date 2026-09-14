package db.migration;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Accept both the V19 schema and a Hibernate-updated schema without losing version values. */
public class V20__add_product_version extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        // Explicit semantic checksum: future schema changes belong in a new migration.
        return 2026091120;
    }

    @Override
    public boolean canExecuteInTransaction() {
        // MySQL/H2 DDL commits implicitly. All steps therefore tolerate a partial prior run.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        JdbcSchema schema = new JdbcSchema(context.getConnection());
        JdbcSchema.Table table = schema.table("products");
        JdbcSchema.Column version = schema.column(table, "version", false);
        String qualified = schema.qualified(table);
        if (version == null) {
            schema.execute("ALTER TABLE " + qualified + " ADD COLUMN " + schema.quote("version")
                    + " BIGINT NOT NULL DEFAULT 0");
            return;
        }
        if (version.generated || !isSignedInteger(version)) {
            throw new SQLException("Cannot safely migrate products.version: expected a non-generated signed integer column");
        }
        if (version.type == Types.BIGINT && !version.nullable && isZero(version.defaultValue)) return;

        String column = schema.quote(version.name);
        if (version.nullable) {
            schema.execute("UPDATE " + qualified + " SET " + column + " = 0 WHERE " + column + " IS NULL");
        }
        if (schema.mysql) {
            schema.execute("ALTER TABLE " + qualified + " MODIFY COLUMN " + column + " BIGINT NOT NULL DEFAULT 0");
        } else {
            if (version.type != Types.BIGINT) {
                schema.execute("ALTER TABLE " + qualified + " ALTER COLUMN " + column + " SET DATA TYPE BIGINT");
            }
            if (!isZero(version.defaultValue)) {
                schema.execute("ALTER TABLE " + qualified + " ALTER COLUMN " + column + " SET DEFAULT 0");
            }
            if (version.nullable) {
                schema.execute("ALTER TABLE " + qualified + " ALTER COLUMN " + column + " SET NOT NULL");
            }
        }
    }

    private boolean isSignedInteger(JdbcSchema.Column column) {
        return (column.type == Types.BIGINT || column.type == Types.INTEGER
                || column.type == Types.SMALLINT || column.type == Types.TINYINT)
                && (column.typeName == null || !column.typeName.toUpperCase(Locale.ROOT).contains("UNSIGNED"));
    }

    private boolean isZero(String value) {
        return value != null && "0".equals(value.replace("(", "").replace(")", "").replace("'", "").trim());
    }
}
