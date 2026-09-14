package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Metadata helpers shared only by the V20/V21 migrations; keep their semantics stable. */
final class JdbcSchema {

    final Connection connection;
    final DatabaseMetaData metadata;
    final boolean mysql;
    private final String quote;

    JdbcSchema(Connection connection) throws SQLException {
        this.connection = connection;
        this.metadata = connection.getMetaData();
        String product = metadata.getDatabaseProductName();
        this.mysql = "MySQL".equalsIgnoreCase(product);
        if (!mysql && !"H2".equalsIgnoreCase(product)) {
            throw new SQLException("V20/V21 support MySQL and H2, not " + product);
        }
        this.quote = mysql ? "`" : "\"";
    }

    Table table(String requestedName) throws SQLException {
        String catalog = connection.getCatalog();
        String schema;
        try {
            schema = connection.getSchema();
        } catch (SQLFeatureNotSupportedException ex) {
            schema = null;
        }
        if (mysql && catalog != null) schema = null;
        Table found = null;
        try (ResultSet rows = metadata.getTables(catalog, schema, "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (!requestedName.equalsIgnoreCase(name)) continue;
                String actualCatalog = rows.getString("TABLE_CAT");
                String actualSchema = rows.getString("TABLE_SCHEM");
                if (catalog != null && !catalog.equals(actualCatalog)) continue;
                if (schema != null && !schema.equals(actualSchema)) continue;
                if (found != null) throw new SQLException("Ambiguous table in current schema: " + requestedName);
                found = new Table(actualCatalog, actualSchema, name);
            }
        }
        if (found == null) throw new SQLException("Required table is missing: " + requestedName);
        return found;
    }

    Column column(Table table, String requestedName, boolean required) throws SQLException {
        Column found = null;
        String escape = metadata.getSearchStringEscape();
        String tablePattern = table.name;
        if (escape != null && !escape.isEmpty()) {
            tablePattern = tablePattern.replace(escape, escape + escape)
                    .replace("_", escape + "_").replace("%", escape + "%");
        }
        try (ResultSet rows = metadata.getColumns(table.catalog, table.schema, tablePattern, "%")) {
            while (rows.next()) {
                if (!table.name.equals(rows.getString("TABLE_NAME"))) continue;
                String name = rows.getString("COLUMN_NAME");
                if (!requestedName.equalsIgnoreCase(name)) continue;
                if (found != null) throw new SQLException("Ambiguous column: " + table.name + "." + requestedName);
                found = new Column(name, rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME"),
                        rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, rows.getString("COLUMN_DEF"),
                        "YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT"))
                                || "YES".equalsIgnoreCase(rows.getString("IS_GENERATEDCOLUMN")));
            }
        }
        if (required && found == null) throw new SQLException("Required column is missing: " + table.name + "." + requestedName);
        return found;
    }

    List<Index> indexes(Table table) throws SQLException {
        Map<String, Index> indexes = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getIndexInfo(table.catalog, table.schema, table.name, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                if (name == null || rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
                Index index = indexes.get(name);
                if (index == null) {
                    index = new Index(name, !rows.getBoolean("NON_UNIQUE"));
                    indexes.put(name, index);
                }
                // A null column denotes an expression index and must never match a plain column index.
                index.columns.put(rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(indexes.values());
    }

    List<String> primaryKeyColumns(Table table) throws SQLException {
        Map<Integer, String> columns = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(table.catalog, table.schema, table.name)) {
            while (rows.next()) columns.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME"));
        }
        return new ArrayList<>(columns.values());
    }

    String qualified(Table table) {
        String namespace = mysql && table.catalog != null ? table.catalog : table.schema;
        return namespace == null || namespace.isEmpty() ? quote(table.name) : quote(namespace) + "." + quote(table.name);
    }

    String quote(String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static boolean sameColumns(List<String> actual, String... expected) {
        if (actual.size() != expected.length) return false;
        List<String> names = new ArrayList<>();
        for (String name : actual) names.add(name == null ? null : name.toLowerCase(Locale.ROOT));
        for (String name : expected) if (!names.remove(name.toLowerCase(Locale.ROOT))) return false;
        return names.isEmpty();
    }

    static final class Table {
        final String catalog;
        final String schema;
        final String name;

        Table(String catalog, String schema, String name) {
            this.catalog = catalog;
            this.schema = schema;
            this.name = name;
        }
    }

    static final class Column {
        final String name;
        final int type;
        final String typeName;
        final boolean nullable;
        final String defaultValue;
        final boolean generated;

        Column(String name, int type, String typeName, boolean nullable, String defaultValue, boolean generated) {
            this.name = name;
            this.type = type;
            this.typeName = typeName;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.generated = generated;
        }
    }

    static final class Index {
        final String name;
        final boolean unique;
        final Map<Integer, String> columns = new TreeMap<>();

        Index(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        boolean matches(String... names) {
            return unique && sameColumns(new ArrayList<>(columns.values()), names);
        }
    }
}
