package com.heihu.workorder.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.sqlscript.SqlScriptFactory;
import org.flywaydb.database.mysql.MySQLDatabaseType;

/** MySQL-only SQL execution adapter for the two immutable session migrations.
 * Flyway 8.5 resolves and checksums the original resource separately from its executable script.
 */
public final class LegacyMySqlMigrationType extends MySQLDatabaseType {
    static final String V15 = "V15__add_user_session_monitor_metadata.sql";
    static final String V18 = "V18__add_user_session_activity_tracking.sql";
    private static final String V15_HASH = "5dc7e823d246d120f0f66ef2eac20324d3eaff64ffdcfb57a5e18a757eaa8171";
    private static final String V18_HASH = "d08b2016768b65e15cc7d6318e6cba9b7beafdb1bd16c843910b1d3a90c83992";
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "ALTER TABLE `user_sessions`\\s+ADD COLUMN IF NOT EXISTS `([a-z_]+)` ([A-Z]+\\([0-9]+\\) NULL);");

    @Override
    public int getPriority() {
        return super.getPriority() + 1;
    }

    @Override
    public SqlScriptFactory createSqlScriptFactory(Configuration config, ParsingContext context) {
        SqlScriptFactory standard = super.createSqlScriptFactory(config, context);
        return (resource, mixed, resources) -> standard.createSqlScript(forExecution(resource), mixed, resources);
    }

    static LoadableResource forExecution(LoadableResource original) {
        String file = original.getFilename();
        if (!V15.equals(file) && !V18.equals(file)) return original;
        String sql;
        try (BufferedReader reader = new BufferedReader(original.read())) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException | java.io.UncheckedIOException ex) {
            throw new FlywayException("Cannot read historical migration " + file, ex);
        }
        String expected = V15.equals(file) ? V15_HASH : V18_HASH;
        if (!expected.equals(sha256(sql))) {
            throw new FlywayException("Historical migration does not match the pinned source: " + file);
        }
        Matcher matcher = ADD_COLUMN.matcher(sql);
        StringBuffer translated = new StringBuffer();
        while (matcher.find()) {
            String column = matcher.group(1);
            String definition = matcher.group(2);
            // Both fragments are from the pinned SQL resource, not runtime input.
            String ddl = "ALTER TABLE `user_sessions` ADD COLUMN `" + column + "` " + definition;
            String conditional = "SET @gongdan_session_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns"
                    + " WHERE table_schema = DATABASE() AND table_name = 'user_sessions' AND column_name = '"
                    + column + "'), 'SELECT 1', '" + ddl + "');\n"
                    + "PREPARE gongdan_session_stmt FROM @gongdan_session_ddl;\n"
                    + "EXECUTE gongdan_session_stmt;\n"
                    + "DEALLOCATE PREPARE gongdan_session_stmt;";
            matcher.appendReplacement(translated, Matcher.quoteReplacement(conditional));
        }
        matcher.appendTail(translated);
        String executionSql = translated.append("\nSET @gongdan_session_ddl = NULL;\n").toString();
        return new LoadableResource() {
            @Override public Reader read() { return new StringReader(executionSql); }
            @Override public String getAbsolutePath() { return original.getAbsolutePath(); }
            @Override public String getAbsolutePathOnDisk() { return original.getAbsolutePathOnDisk(); }
            @Override public String getFilename() { return original.getFilename(); }
            @Override public String getRelativePath() { return original.getRelativePath(); }
        };
    }

    private static String sha256(String sql) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8));
            return String.format(Locale.ROOT, "%064x", new BigInteger(1, digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
