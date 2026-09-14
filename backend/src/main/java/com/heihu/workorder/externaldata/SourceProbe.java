package com.heihu.workorder.externaldata;

import org.springframework.stereotype.Component;
import com.heihu.workorder.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.conn.DnsResolver;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import java.net.*;
import javax.net.ssl.SSLException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.annotation.PreDestroy;

/** All untrusted I/O is isolated from request transactions and the core database pool. */
@Component
public class SourceProbe {
    private final SourcePolicy policy;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "external-source-io"); t.setDaemon(true); return t; });
    // JDBC abort/close is driver code: never let it block another request's deadline.
    private final ThreadPoolExecutor cleanup = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "external-source-cleanup"); t.setDaemon(true); return t; });
    private final Semaphore[] gates = new Semaphore[64];
    private final long[] nextAllowed = new long[64];
    public SourceProbe(SourcePolicy policy) {
        this.policy = policy;
        for (int i=0; i<gates.length; i++) gates[i] = new Semaphore(2);
    }
    @PreDestroy public void close() { workers.shutdownNow(); cleanup.shutdownNow(); }

    private interface ProbeWork<T> { T run(Deadline deadline) throws Exception; }
    private static final class Deadline {
        private final long expiresAt;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<Runnable> abort = new AtomicReference<>();
        Deadline(int seconds) { expiresAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds); }
        long remainingNanos() throws TimeoutException, InterruptedException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long remaining = expiresAt - System.nanoTime();
            if (cancelled.get() || remaining <= 0) throw new TimeoutException();
            return remaining;
        }
        int remainingMillis() throws TimeoutException, InterruptedException {
            return (int)Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos()));
        }
        int remainingSeconds() throws TimeoutException, InterruptedException {
            return Math.max(1, (int)TimeUnit.NANOSECONDS.toSeconds(remainingNanos()));
        }
        void onCancel(Runnable action) {
            abort.set(action);
            if (cancelled.get()) runAbort();
        }
        void cancel() { cancelled.set(true); runAbort(); }
        void clear() { abort.set(null); }
        private void runAbort() { Runnable action = abort.getAndSet(null); if (action != null) action.run(); }
    }

    public SourceCheck test(SourceConfig c, String secret, Long factory) {
        long start = System.nanoTime();
        SourceCheck result;
        try {
            result = bounded(factory, c.timeoutSeconds, deadline -> {
                if ("HTTP".equals(c.kind)) return http(c, secret, factory, deadline);
                try (Connection connection = database(c, secret, factory, deadline)) {
                    try (Statement statement = connection.createStatement()) {
                        deadline.onCancel(() -> abortConnection(connection, statement));
                        statement.setQueryTimeout(deadline.remainingSeconds());
                        try (ResultSet rs = statement.executeQuery("SELECT 1")) {
                            if (!rs.next()) throw new SQLException();
                        }
                    }
                    return outcome("UP", null, "数据库连接成功；表字段权限需单独查看", null);
                }
            });
        } catch (ApiException e) { throw e; }
        catch (Exception e) { result = failure(e); }
        result.durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        result.finishedAt = Instant.now();
        return result;
    }
    private <T> T bounded(Long factory, int seconds, ProbeWork<T> work) throws Exception {
        Deadline deadline = new Deadline(seconds);
        int bucket = Math.floorMod(factory.hashCode(), gates.length);
        synchronized (nextAllowed) {
            long now = System.nanoTime();
            if (now < nextAllowed[bucket]) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "检查过于频繁，请稍后再试");
            nextAllowed[bucket] = now + TimeUnit.SECONDS.toNanos(2);
        }
        if (!gates[bucket].tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "当前工厂外部连接繁忙");
        Future<T> future;
        AtomicInteger execution = new AtomicInteger(); // 0=not started, 1=owns permit, 2=released
        try { future = workers.submit(() -> {
            if (!execution.compareAndSet(0,1)) throw new TimeoutException();
            try {
                deadline.remainingNanos();
                T result = work.run(deadline);
                deadline.remainingNanos();
                return result;
            } finally { deadline.clear(); execution.set(2); gates[bucket].release(); }
        }); }
        catch (RejectedExecutionException e) { gates[bucket].release(); throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "外部连接资源繁忙，请稍后重试"); }
        try { return future.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS); }
        catch (TimeoutException e) {
            deadline.cancel(); future.cancel(true);
            if (execution.compareAndSet(0,2)) gates[bucket].release();
            throw e;
        }
        catch (InterruptedException e) {
            deadline.cancel(); future.cancel(true);
            if (execution.compareAndSet(0,2)) gates[bucket].release();
            Thread.currentThread().interrupt(); throw e;
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw new IOException("外部检查失败");
        }
    }
    private SourceCheck http(SourceConfig c, String secret, Long factory, Deadline deadline) throws Exception {
        if (SourcePolicy.blank(c.healthPath)) return outcome("UNKNOWN", "NO_HEALTH_PATH", "未配置健康检查路径", null);
        URI uri = policy.healthUri(c, true);
        InetAddress[] pinned = policy.resolve(factory, uri.getHost(), policy.port(uri), "https".equals(uri.getScheme()));
        deadline.remainingNanos();
        DnsResolver resolver = host -> {
            if (!host.equalsIgnoreCase(uri.getHost())) throw new UnknownHostException();
            return pinned.clone();
        };
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(
            RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", SSLConnectionSocketFactory.getSocketFactory()).build(), resolver);
        HttpGet request = new HttpGet(uri);
        try {
        deadline.onCancel(request::abort);
        request.setConfig(RequestConfig.custom().setConnectTimeout(Math.min(3000, deadline.remainingMillis()))
            .setSocketTimeout(deadline.remainingMillis()).setConnectionRequestTimeout(Math.min(1000, deadline.remainingMillis())).build());
        request.setHeader("Accept", "application/json");
        if ("BEARER".equals(c.authType)) request.setHeader("Authorization", "Bearer " + secret);
        if ("API_KEY".equals(c.authType)) request.setHeader(c.headerName, secret);
        if ("BASIC".equals(c.authType)) request.setHeader("Authorization", "Basic " +
            Base64.getEncoder().encodeToString((c.username + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        try (CloseableHttpClient client = HttpClients.custom().setConnectionManager(manager)
            .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().disableContentCompression().build();
            CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            SourceCheck result = httpOutcome(status);
            if (response.getEntity() != null) {
                if (response.getEntity().getContentEncoding() != null &&
                    !"identity".equalsIgnoreCase(response.getEntity().getContentEncoding().getValue()))
                    return outcome("FAILED", "ENCODING", "健康接口返回不支持的压缩内容", status);
                try (InputStream body = response.getEntity().getContent()) {
                    byte[] buffer = new byte[4096]; int total = 0, read;
                    while ((read = body.read(buffer)) != -1) {
                        deadline.remainingNanos();
                        total += read;
                        if (total > 65536) { request.abort(); return outcome("FAILED", "RESPONSE_TOO_LARGE", "健康响应超过 64 KiB", status); }
                    }
                }
            }
            return result;
        }
        } finally { request.abort(); manager.close(); }
    }
    static SourceCheck httpOutcome(int status) {
        if (status >= 200 && status < 300) return outcome("UP", null, "健康接口可达；未验证业务数据与业务鉴权", status);
        if (status == 401 || status == 403) return outcome("FAILED", "AUTH", "被检查接口认证或权限失败", status);
        if (status >= 300 && status < 400) return outcome("FAILED", "REDIRECT", "接口返回重定向，已阻止跟随", status);
        if (status == 404) return outcome("FAILED", "NOT_FOUND", "健康接口路径不存在", status);
        if (status == 429) return outcome("FAILED", "RATE_LIMIT", "对方接口请求受限", status);
        return outcome("FAILED", "HTTP_RESPONSE", "接口返回非成功状态", status);
    }
    private Connection database(SourceConfig c, String secret, Long factory, Deadline deadline) throws Exception {
        Properties p = new Properties();
        String url;
        if ("SQLITE".equals(c.driver)) {
            url = "jdbc:sqlite:" + policy.sqliteFile(factory, c.fileId).toUri().toASCIIString() + "?mode=ro";
            p.setProperty("open_mode", "1"); p.setProperty("busy_timeout", String.valueOf(deadline.remainingMillis()));
        } else {
            policy.resolve(factory, c.host, c.port, c.tls);
            p.setProperty("user", c.username); p.setProperty("password", secret);
            if ("MYSQL".equals(c.driver)) {
                url = "jdbc:mysql://" + c.host + ":" + c.port + "/" + c.database;
                p.setProperty("sslMode", c.tls ? "VERIFY_IDENTITY" : "DISABLED");
                p.setProperty("allowLoadLocalInfile", "false"); p.setProperty("allowUrlInLocalInfile", "false");
                p.setProperty("allowMultiQueries", "false"); p.setProperty("allowPublicKeyRetrieval", "false");
                p.setProperty("connectTimeout", String.valueOf(Math.min(3000, deadline.remainingMillis())));
                p.setProperty("socketTimeout", String.valueOf(deadline.remainingMillis()));
            } else if ("POSTGRESQL".equals(c.driver)) {
                url = "jdbc:postgresql://" + c.host + ":" + c.port + "/" + c.database;
                p.setProperty("sslmode", c.tls ? "verify-full" : "disable");
                p.setProperty("connectTimeout", String.valueOf(Math.min(3, deadline.remainingSeconds())));
                p.setProperty("socketTimeout", String.valueOf(deadline.remainingSeconds()));
                p.setProperty("loginTimeout", String.valueOf(deadline.remainingSeconds()));
                p.setProperty("readOnly", "true"); p.setProperty("readOnlyMode", "always");
            } else {
                url = "jdbc:sqlserver://" + c.host + ":" + c.port;
                p.setProperty("databaseName", c.database); p.setProperty("encrypt", String.valueOf(c.tls));
                p.setProperty("trustServerCertificate", "false"); p.setProperty("loginTimeout", String.valueOf(deadline.remainingSeconds()));
                p.setProperty("socketTimeout", String.valueOf(deadline.remainingMillis()));
                p.setProperty("applicationIntent", "ReadOnly"); p.setProperty("transparentNetworkIPResolution", "false");
            }
        }
        deadline.remainingNanos();
        Connection conn = connect(url, p);
        try {
            deadline.onCancel(() -> abortConnection(conn, null));
            deadline.remainingNanos();
            if ("SQLITE".equals(c.driver)) {
                try (Statement s = conn.createStatement()) {
                    deadline.onCancel(() -> abortConnection(conn, s));
                    deadline.remainingNanos(); s.execute("PRAGMA query_only=ON");
                }
            } else conn.setReadOnly(true);
            return conn;
        } catch (Exception e) { conn.close(); throw e; }
    }
    Connection connect(String url, Properties properties) throws SQLException { return DriverManager.getConnection(url, properties); }
    private void abortConnection(Connection connection, Statement statement) {
        try {
            cleanup.execute(() -> {
                // Xerial's Connection.abort is a no-op; Statement.cancel invokes native sqlite3_interrupt.
                if (statement != null) {
                    try { statement.cancel(); } catch (SQLException | RuntimeException | AbstractMethodError ignored) { }
                }
                try { connection.abort(Runnable::run); }
                catch (SQLException | RuntimeException | AbstractMethodError ignored) { }
                // Also close when a driver silently does not implement abort.
                try { connection.close(); } catch (SQLException | RuntimeException ignored) { }
            });
        } catch (RejectedExecutionException ignored) {
            // The I/O worker still owns try-with-resources and the finite driver socket timeout.
        }
    }
    public Map<String,Object> schema(SourceConfig c, String secret, Long factory, String table, int page) {
        if (!"DATABASE".equals(c.kind)) throw new IllegalArgumentException("HTTP API 不支持表字段浏览");
        if (page < 0 || page > 100) throw new IllegalArgumentException("页码超出范围");
        if (table != null && (table.length() > 128 || table.indexOf('\0') >= 0)) throw new IllegalArgumentException("表名无效");
        try {
            return bounded(factory, c.timeoutSeconds, deadline -> {
                try (Connection conn = database(c, secret, factory, deadline)) {
                    if ("SQLITE".equals(c.driver)) return sqliteSchema(conn, table, page, deadline);
                    DatabaseMetaData meta = conn.getMetaData();
                    String catalog = c.database;
                    String schema = SourcePolicy.blank(c.schema) ? null : c.schema;
                    List<Map<String,Object>> rows = new ArrayList<>();
                    boolean more = false;
                    if (table == null || table.isEmpty()) {
                        try (ResultSet rs = meta.getTables(catalog, escapePattern(meta, schema), "%", new String[]{"TABLE", "VIEW"})) {
                            int skipped = 0;
                            while (rs.next()) {
                                deadline.remainingNanos();
                                if (!matchesScope(rs, catalog, schema)) continue;
                                if (skipped++ < page * 100) continue;
                                if (rows.size() == 100) { more = true; break; }
                                Map<String,Object> row = new LinkedHashMap<>();
                                row.put("schema", rs.getString("TABLE_SCHEM")); row.put("table", rs.getString("TABLE_NAME"));
                                row.put("type", rs.getString("TABLE_TYPE")); rows.add(row);
                            }
                        }
                    } else {
                        Set<String> primaryKeys = new HashSet<>();
                        try (ResultSet pk = meta.getPrimaryKeys(catalog, schema, table)) {
                            while (pk.next()) {
                                deadline.remainingNanos();
                                if (!matchesScope(pk, catalog, schema) || !table.equals(pk.getString("TABLE_NAME"))) continue;
                                primaryKeys.add(pk.getString("COLUMN_NAME"));
                                if (primaryKeys.size() > 256) throw new ApiException(HttpStatus.BAD_GATEWAY,"主键超过一期支持的 256 列上限，未返回不完整字段信息");
                            }
                        }
                        // Exact table selected from metadata; no SQL is assembled from this value.
                        try (ResultSet rs = meta.getColumns(catalog, escapePattern(meta, schema), escapePattern(meta, table), "%")) {
                            int skipped = 0;
                            while (rs.next()) {
                                deadline.remainingNanos();
                                if (!matchesScope(rs, catalog, schema) || !table.equals(rs.getString("TABLE_NAME"))) continue;
                                if (skipped++ < page * 100) continue;
                                if (rows.size() == 100) { more = true; break; }
                                Map<String,Object> row = new LinkedHashMap<>();
                                row.put("schema", rs.getString("TABLE_SCHEM")); row.put("column", rs.getString("COLUMN_NAME"));
                                row.put("type", rs.getString("TYPE_NAME")); row.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                                row.put("primaryKey", primaryKeys.contains(rs.getString("COLUMN_NAME")));
                                rows.add(row);
                            }
                        }
                    }
                    return metadataResult(rows, more, page);
                }
            });
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "表字段读取失败：" + failure(e).message); }
    }
    private Map<String,Object> sqliteSchema(Connection connection, String table, int page, Deadline deadline) throws Exception {
        boolean columns = table != null && !table.isEmpty();
        // Xerial getColumns builds a UNION per column and fails for valid tables wider than 500 columns.
        // Only SQLite metadata is queried here; all caller values are bound, never SQL identifiers.
        String sql = columns
            ? "SELECT name, type, \"notnull\" AS column_not_null, pk FROM pragma_table_xinfo(?, 'main') ORDER BY cid LIMIT ? OFFSET ?"
            : "SELECT name, upper(type) AS table_type FROM main.sqlite_schema WHERE type IN ('table','view') "
                + "AND substr(lower(name),1,7) <> 'sqlite_' ORDER BY table_type, name LIMIT ? OFFSET ?";
        List<Map<String,Object>> rows = new ArrayList<>(); boolean more = false;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            deadline.onCancel(() -> abortConnection(connection, statement));
            statement.setQueryTimeout(deadline.remainingSeconds());
            int index = 1;
            if (columns) statement.setString(index++, table);
            statement.setInt(index++, 101); statement.setInt(index, page * 100);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    deadline.remainingNanos();
                    if (rows.size() == 100) { more = true; break; }
                    Map<String,Object> row = new LinkedHashMap<>(); row.put("schema", null);
                    if (columns) {
                        row.put("column", result.getString("name")); row.put("type", result.getString("type"));
                        row.put("nullable", result.getInt("column_not_null") == 0); row.put("primaryKey", result.getInt("pk") > 0);
                    } else {
                        row.put("table", result.getString("name")); row.put("type", result.getString("table_type"));
                    }
                    rows.add(row);
                }
            }
        }
        return metadataResult(rows, more, page);
    }
    private Map<String,Object> metadataResult(List<Map<String,Object>> rows, boolean more, int page) {
        boolean truncated = more && page == 100;
        Map<String,Object> result = new LinkedHashMap<>(); result.put("items", rows); result.put("hasMore", more && !truncated);
        result.put("truncated", truncated);
        result.put("message", truncated ? "已达到一期最多 10,100 条元数据的浏览上限" :
            rows.isEmpty() ? "未返回元数据：可能为空，也可能账号没有可见权限" : "只读元数据，不读取业务记录");
        return result;
    }
    private boolean matchesScope(ResultSet row, String catalog, String schema) throws SQLException {
        return (catalog == null || catalog.equals(row.getString("TABLE_CAT")))
            && (schema == null || schema.equals(row.getString("TABLE_SCHEM")));
    }
    private String escapePattern(DatabaseMetaData meta, String value) throws SQLException {
        if (value == null) return null;
        String escape = meta.getSearchStringEscape();
        return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }
    static SourceCheck failure(Exception e) {
        if (e instanceof UnknownHostException) return outcome("FAILED", "DNS", "目标地址解析失败", null);
        if (e instanceof SSLException) return outcome("FAILED", "TLS", "TLS 证书或握手失败", null);
        if (e instanceof TimeoutException || e instanceof java.io.InterruptedIOException || e instanceof SQLTimeoutException || e instanceof InterruptedException)
            return outcome("FAILED", "TIMEOUT", "检查超时", null);
        if (e instanceof SQLException) {
            String state = ((SQLException)e).getSQLState();
            return outcome("FAILED", state != null && state.startsWith("28") ? "DB_AUTH" : "DB_CONNECT",
                state != null && state.startsWith("28") ? "数据库认证失败" : "数据库连接或只读检查失败，请检查账号、TLS及驱动配置", null);
        }
        if (e instanceof IllegalArgumentException) return outcome("FAILED", "POLICY", "目标地址或文件未获出口策略授权", null);
        return outcome("FAILED", "NETWORK", "外部连接失败，请检查网络、目标服务或文件是否可用", null);
    }
    static SourceCheck outcome(String status, String code, String message, Integer httpStatus) {
        SourceCheck r = new SourceCheck(); r.status = status; r.errorCode = code; r.message = message; r.httpStatus = httpStatus; return r;
    }
}
