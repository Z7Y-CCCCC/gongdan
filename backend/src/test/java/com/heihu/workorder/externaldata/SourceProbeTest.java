package com.heihu.workorder.externaldata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SourceProbeTest {
    @TempDir Path root;
    private SourceProbe probe;
    private HttpServer server;
    @AfterEach void close() { if(probe!=null) probe.close(); if(server!=null) server.stop(0); }
    @Test void classifiesHttpStatusesWithoutClaimingBusinessHealth() {
        assertThat(SourceProbe.httpOutcome(200).message).contains("未验证业务");
        assertThat(SourceProbe.httpOutcome(204).status).isEqualTo("UP");
        assertThat(SourceProbe.httpOutcome(302).errorCode).isEqualTo("REDIRECT");
        assertThat(SourceProbe.httpOutcome(401).errorCode).isEqualTo("AUTH");
        assertThat(SourceProbe.httpOutcome(403).errorCode).isEqualTo("AUTH");
        assertThat(SourceProbe.httpOutcome(404).errorCode).isEqualTo("NOT_FOUND");
        assertThat(SourceProbe.httpOutcome(429).errorCode).isEqualTo("RATE_LIMIT");
        assertThat(SourceProbe.httpOutcome(500).errorCode).isEqualTo("HTTP_RESPONSE");
        assertThat(SourceProbe.failure(new java.net.UnknownHostException()).errorCode).isEqualTo("DNS");
        assertThat(SourceProbe.failure(new org.apache.http.conn.ConnectTimeoutException()).errorCode).isEqualTo("TIMEOUT");
        assertThat(SourceProbe.failure(new javax.net.ssl.SSLException("secret")).message).doesNotContain("secret");
        assertThat(SourceProbe.failure(new SQLInvalidAuthorizationSpecException("secret","28000")).errorCode).isEqualTo("DB_AUTH");
    }
    private SourceConfig start(int status, byte[] body, AtomicReference<String> auth, String authHeader) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/health", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst(authHeader));
            exchange.getResponseHeaders().add("Location","http://127.0.0.1/should-not-follow");
            exchange.sendResponseHeaders(status,body.length);
            try(java.io.OutputStream out=exchange.getResponseBody()) { out.write(body); }
        }); server.start();
        SourcePolicy policy=mock(SourcePolicy.class);
        SourceConfig c=new SourceConfig(); c.baseUrl="http://test.invalid:"+server.getAddress().getPort()+"/api"; c.healthPath="/health";
        URI uri=new URI(c.baseUrl+c.healthPath);
        when(policy.healthUri(c,true)).thenReturn(uri); when(policy.port(uri)).thenReturn(server.getAddress().getPort());
        when(policy.resolve(anyLong(),anyString(),anyInt(),anyBoolean())).thenReturn(new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        probe=new SourceProbe(policy); return c;
    }
    @Test void sendsBearerOnlyFromBackendAndDoesNotFollowRedirect() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>();
        SourceConfig c=start(302,"redirect".getBytes(),auth,"Authorization"); c.authType="BEARER";
        SourceCheck check=probe.test(c,"test-token",1L);
        assertThat(auth.get()).isEqualTo("Bearer test-token"); assertThat(check.errorCode).isEqualTo("REDIRECT");
        assertThat(check.httpStatus).isEqualTo(302); assertThat(check.message).doesNotContain("test-token");
    }
    @Test void sendsBasic() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>(); SourceConfig c=start(200,"{}".getBytes(),auth,"Authorization");
        c.authType="BASIC"; c.username="operator";
        assertThat(probe.test(c,"pw",1L).status).isEqualTo("UP");
        assertThat(auth.get()).isEqualTo("Basic "+Base64.getEncoder().encodeToString("operator:pw".getBytes("UTF-8")));
    }
    @Test void sendsApiKey() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>(); SourceConfig c=start(200,"{}".getBytes(),auth,"X-API-Key");
        c.authType="API_KEY"; c.headerName="X-API-Key";
        assertThat(probe.test(c,"key-value",1L).status).isEqualTo("UP"); assertThat(auth.get()).isEqualTo("key-value");
    }
    @Test void boundsResponseAndDoesNotSendCredentialsForAnonymous() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>(); SourceConfig c=start(200,new byte[65537],auth,"Authorization");
        assertThat(probe.test(c,"ignored",1L).errorCode).isEqualTo("RESPONSE_TOO_LARGE"); assertThat(auth.get()).isNull();
    }
    @Test void noHealthPathDoesNotPerformNetworkCall() {
        SourcePolicy policy=mock(SourcePolicy.class); probe=new SourceProbe(policy);
        assertThat(probe.test(new SourceConfig(),"",1L).errorCode).isEqualTo("NO_HEALTH_PATH"); verifyNoInteractions(policy);
    }
    @Test void sqliteRealDriverBrowsesTablesAndColumnsWithoutChangingFile() throws Exception {
        Path file=root.resolve("sample.db");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toString()); Statement s=c.createStatement()) {
            s.execute("CREATE TABLE samples (id INTEGER PRIMARY KEY, temperature REAL NOT NULL)");
            s.execute("INSERT INTO samples VALUES (1,36.8)");
        }
        byte[] before=Files.readAllBytes(file);
        SourcePolicy policy=new SourcePolicy("","","","1@sample,2@sample,3@sample",root.toString());
        probe=new SourceProbe(policy);
        SourceConfig c=new SourceConfig(); c.kind="DATABASE"; c.driver="SQLITE"; c.fileId="sample";
        assertThat(probe.test(c,"",1L).status).isEqualTo("UP");
        Map<String,Object> tables=probe.schema(c,"",2L,"",0);
        assertThat(tables.toString()).contains("samples");
        Map<String,Object> columns=probe.schema(c,"",3L,"samples",0);
        assertThat(columns.toString()).contains("temperature","primaryKey=true","nullable=false");
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
    @Test void repeatedCallsAreRateLimited() {
        probe=new SourceProbe(mock(SourcePolicy.class)); probe.test(new SourceConfig(),"",1L);
        assertThatThrownBy(() -> probe.test(new SourceConfig(),"",1L)).hasMessageContaining("频繁");
    }
    @Test void slowResponseHonorsTotalDeadline() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>();
        SourceConfig c=start(200,"{}".getBytes(),auth,"Authorization");
        server.removeContext("/api/health");
        server.createContext("/api/health", exchange -> {
            try { Thread.sleep(1800); exchange.sendResponseHeaders(200,2); exchange.getResponseBody().write("{}".getBytes()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        c.timeoutSeconds=1;
        long start=System.nanoTime();
        assertThat(probe.test(c,"",1L).errorCode).isEqualTo("TIMEOUT");
        assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1600);
    }
    @Test void lateDnsResolutionCannotStartAnHttpRequestAfterDeadline() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>();
        SourceConfig c=start(200,"{}".getBytes(),auth,"Authorization"); c.authType="BEARER"; c.timeoutSeconds=1;
        SourcePolicy policy=(SourcePolicy)org.springframework.test.util.ReflectionTestUtils.getField(probe,"policy");
        CountDownLatch releaseDns=new CountDownLatch(1), dnsFinished=new CountDownLatch(1);
        when(policy.resolve(anyLong(),anyString(),anyInt(),anyBoolean())).thenAnswer(invocation -> {
            awaitUninterruptibly(releaseDns); dnsFinished.countDown();
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        });
        try {
            assertThat(probe.test(c,"do-not-send",1L).errorCode).isEqualTo("TIMEOUT");
        } finally { releaseDns.countDown(); }
        assertThat(dnsFinished.await(2,TimeUnit.SECONDS)).isTrue();
        awaitWorkersIdle();
        assertThat(auth.get()).isNull();
    }
    @Test void slowTrickleIsAbortedAndReleasesWorkerAtTheTotalDeadline() throws Exception {
        AtomicReference<String> auth=new AtomicReference<>();
        SourceConfig c=start(200,"{}".getBytes(),auth,"Authorization"); c.timeoutSeconds=1;
        server.removeContext("/api/health");
        server.createContext("/api/health", exchange -> {
            try {
                exchange.sendResponseHeaders(200,0);
                for (int i=0;i<20;i++) {
                    exchange.getResponseBody().write(' '); exchange.getResponseBody().flush();
                    Thread.sleep(150);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        long started=System.nanoTime();
        assertThat(probe.test(c,"",1L).errorCode).isEqualTo("TIMEOUT");
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(1600);
        awaitWorkersIdle();
    }
    private void awaitWorkersIdle() throws InterruptedException {
        ThreadPoolExecutor workers=(ThreadPoolExecutor)org.springframework.test.util.ReflectionTestUtils.getField(probe,"workers");
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while (workers.getActiveCount()!=0 && System.nanoTime()<until) Thread.sleep(10);
        assertThat(workers.getActiveCount()).isZero();
    }
    private void awaitUninterruptibly(CountDownLatch latch) throws InterruptedException {
        boolean interrupted=false;
        try {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try {
                    if (!latch.await(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS))
                        throw new AssertionError("test resource was not released");
                    return;
                } catch (InterruptedException e) { interrupted=true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    private SourceConfig mockedDatabase(Connection connection) throws Exception {
        probe=spy(new SourceProbe(mock(SourcePolicy.class)));
        doReturn(connection).when(probe).connect(anyString(),any(Properties.class));
        SourceConfig c=new SourceConfig(); c.kind="DATABASE"; c.driver="POSTGRESQL";
        c.host="10.20.30.40"; c.database="plant"; c.schema="plant_a"; c.username="reader";
        return c;
    }
    @Test void jdbcDeadlineAbortsActiveConnectionWithoutBlockingRequestThread() throws Exception {
        Connection connection=mock(Connection.class); Statement statement=mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        SourceConfig c=mockedDatabase(connection); c.timeoutSeconds=1;
        CountDownLatch releaseQuery=new CountDownLatch(1), aborted=new CountDownLatch(1);
        when(statement.executeQuery("SELECT 1")).thenAnswer(invocation -> {
            awaitUninterruptibly(releaseQuery); throw new SQLException("aborted query with secret context");
        });
        doAnswer(invocation -> { aborted.countDown(); releaseQuery.countDown(); return null; })
            .when(connection).abort(any(Executor.class));
        try {
            long started=System.nanoTime();
            SourceCheck result=probe.test(c,"secret-password",1L);
            assertThat(result.errorCode).isEqualTo("TIMEOUT");
            assertThat(result.message).doesNotContain("secret");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(1600);
            assertThat(aborted.await(2,TimeUnit.SECONDS)).isTrue();
            awaitWorkersIdle(); verify(connection,atLeastOnce()).close();
        } finally { releaseQuery.countDown(); }
    }
    @Test void lateJdbcConnectionIsClosedWithoutExecutingAQuery() throws Exception {
        Connection connection=mock(Connection.class); SourceConfig c=mockedDatabase(connection); c.timeoutSeconds=1;
        CountDownLatch releaseConnect=new CountDownLatch(1);
        doAnswer(invocation -> { awaitUninterruptibly(releaseConnect); return connection; })
            .when(probe).connect(anyString(),any(Properties.class));
        try { assertThat(probe.test(c,"secret-password",1L).errorCode).isEqualTo("TIMEOUT"); }
        finally { releaseConnect.countDown(); }
        awaitWorkersIdle();
        verify(connection,atLeastOnce()).close(); verify(connection,never()).createStatement();
        verify(connection,never()).setReadOnly(anyBoolean());
    }
    private Map<String,Object> metadataRow(String catalog, String schema, String table, String column) {
        Map<String,Object> row=new HashMap<>(); row.put("TABLE_CAT",catalog); row.put("TABLE_SCHEM",schema);
        row.put("TABLE_NAME",table); row.put("COLUMN_NAME",column); row.put("TABLE_TYPE","TABLE");
        row.put("TYPE_NAME","VARCHAR"); row.put("NULLABLE",DatabaseMetaData.columnNullable); return row;
    }
    private ResultSet metadataRows(List<Map<String,Object>> rows) throws Exception {
        ResultSet result=mock(ResultSet.class); AtomicInteger index=new AtomicInteger(-1);
        when(result.next()).thenAnswer(invocation -> index.incrementAndGet()<rows.size());
        when(result.getString(anyString())).thenAnswer(invocation -> (String)rows.get(index.get()).get(invocation.getArgument(0)));
        when(result.getInt(anyString())).thenAnswer(invocation -> (Integer)rows.get(index.get()).get(invocation.getArgument(0)));
        return result;
    }
    @Test void metadataCannotEscapeExactCatalogAndSchemaEvenIfDriverIgnoresPatterns() throws Exception {
        Connection connection=mock(Connection.class); DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta); when(meta.getSearchStringEscape()).thenReturn("\\");
        SourceConfig c=mockedDatabase(connection);
        ResultSet tables=metadataRows(Arrays.asList(
            metadataRow("other","plant_a","other_catalog",null), metadataRow("plant","plantXa","other_schema",null),
            metadataRow("plant","plant_a","samples",null)));
        when(meta.getTables(eq("plant"),eq("plant\\_a"),eq("%"),any(String[].class))).thenReturn(tables);
        assertThat(probe.schema(c,"pw",1L,"",0).toString()).contains("samples").doesNotContain("other_catalog","other_schema");
        ResultSet primaryKeys=metadataRows(Arrays.asList(
            metadataRow("plant","plantXa","samples","label"), metadataRow("plant","plant_a","samples","id")));
        when(meta.getPrimaryKeys("plant","plant_a","samples")).thenReturn(primaryKeys);
        ResultSet visibleColumns=metadataRows(Arrays.asList(
            metadataRow("plant","plantXa","samples","hidden"), metadataRow("other","plant_a","samples","hidden2"),
            metadataRow("plant","plant_a","samples","id"), metadataRow("plant","plant_a","samples","label")));
        when(meta.getColumns("plant","plant\\_a","samples","%")).thenReturn(visibleColumns);
        Map<String,Object> result=probe.schema(c,"pw",2L,"samples",0);
        @SuppressWarnings("unchecked") List<Map<String,Object>> columns=(List<Map<String,Object>>)result.get("items");
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0)).containsEntry("column","id").containsEntry("primaryKey",true);
        assertThat(columns.get(1)).containsEntry("column","label").containsEntry("primaryKey",false);
    }
    @Test void lastPermittedMetadataPageDoesNotOfferAnImpossibleNextPage() throws Exception {
        Connection connection=mock(Connection.class); DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta); when(meta.getSearchStringEscape()).thenReturn("\\");
        SourceConfig c=mockedDatabase(connection); c.timeoutSeconds=30;
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<10101;i++) rows.add(metadataRow("plant","plant_a","table"+i,null));
        ResultSet tables=metadataRows(rows);
        when(meta.getTables(eq("plant"),eq("plant\\_a"),eq("%"),any(String[].class))).thenReturn(tables);
        Map<String,Object> result=probe.schema(c,"pw",1L,"",100);
        assertThat((List<?>)result.get("items")).hasSize(100);
        assertThat(result).containsEntry("hasMore",false).containsEntry("truncated",true);
        assertThat(result.get("message").toString()).contains("浏览上限");
    }
    private SourceConfig sqliteConfig() {
        SourceConfig c=new SourceConfig(); c.kind="DATABASE"; c.driver="SQLITE"; c.fileId="sample"; return c;
    }
    @Test void sqlitePagesBeyondDriverCompoundSelectLimitAndBindsLiteralTableName() throws Exception {
        Path file=root.resolve("sample.db");
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+file); Statement statement=connection.createStatement()) {
            StringBuilder sql=new StringBuilder("CREATE TABLE wide_table (c0 INTEGER PRIMARY KEY");
            for (int i=1;i<=550;i++) sql.append(", c").append(i).append(" TEXT NOT NULL");
            statement.execute(sql.append(")").toString());
            statement.execute("CREATE TABLE \"odd'table_name\" (id TEXT PRIMARY KEY, value REAL NOT NULL)");
            statement.execute("CREATE VIEW wide_view AS SELECT c0, c550 FROM wide_table");
        }
        byte[] before=Files.readAllBytes(file);
        probe=new SourceProbe(new SourcePolicy("","","","1@sample,2@sample,3@sample,4@sample,5@sample",root.toString()));
        SourceConfig c=sqliteConfig();
        Map<String,Object> first=probe.schema(c,"",1L,"wide_table",0);
        @SuppressWarnings("unchecked") List<Map<String,Object>> firstColumns=(List<Map<String,Object>>)first.get("items");
        assertThat(firstColumns).hasSize(100);
        assertThat(firstColumns.get(0)).containsEntry("column","c0").containsEntry("primaryKey",true);
        assertThat(first).containsEntry("hasMore",true);
        Map<String,Object> last=probe.schema(c,"",2L,"wide_table",5);
        assertThat((List<?>)last.get("items")).hasSize(51); assertThat(last).containsEntry("hasMore",false);
        assertThat(last.toString()).contains("c550");
        Map<String,Object> quoted=probe.schema(c,"",3L,"odd'table_name",0);
        assertThat((List<?>)quoted.get("items")).hasSize(2);
        assertThat(probe.schema(c,"",4L,"wide_table' OR 1=1 --",0).get("items")).isEqualTo(Collections.emptyList());
        assertThat(probe.schema(c,"",5L,"",0).toString()).contains("wide_table","wide_view","type=VIEW");
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
    @Test void sqliteNativeQueryIsInterruptedRatherThanRelyingOnNoopConnectionAbort() throws Exception {
        Path file=root.resolve("sample.db");
        try(Connection setup=DriverManager.getConnection("jdbc:sqlite:"+file); Statement statement=setup.createStatement()) {
            statement.execute("CREATE TABLE samples(id INTEGER)");
        }
        Connection nativeConnection=DriverManager.getConnection("jdbc:sqlite:"+file.toUri().toASCIIString()+"?mode=ro");
        Connection observed=spy(nativeConnection);
        // Simulate expensive native metadata work without requiring a giant customer database.
        String slowMetadata="WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000000000) "
            + "SELECT 'samples' AS name, 'TABLE' AS table_type, sum(x) AS total FROM n LIMIT ? OFFSET ?";
        PreparedStatement running=spy(nativeConnection.prepareStatement(slowMetadata));
        doReturn(running).when(observed).prepareStatement(startsWith("SELECT name, upper(type)"));
        probe=spy(new SourceProbe(new SourcePolicy("","","","1@sample",root.toString())));
        doReturn(observed).when(probe).connect(anyString(),any(Properties.class));
        SourceConfig c=sqliteConfig(); c.timeoutSeconds=1;
        try {
            long started=System.nanoTime();
            assertThatThrownBy(() -> probe.schema(c,"",1L,"",0)).hasMessageContaining("超时");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(1600);
            verify(running,timeout(2000)).cancel();
            awaitWorkersIdle(); assertThat(nativeConnection.isClosed()).isTrue();
        } finally {
            if (!nativeConnection.isClosed()) running.cancel();
            nativeConnection.close();
        }
    }
    @Test void exclusivelyLockedSqliteMetadataRemainsBoundedAndClosesItsWorker() throws Exception {
        Path file=root.resolve("sample.db");
        try(Connection writer=DriverManager.getConnection("jdbc:sqlite:"+file); Statement statement=writer.createStatement()) {
            statement.execute("CREATE TABLE samples(id INTEGER)");
            statement.execute("BEGIN EXCLUSIVE");
            probe=new SourceProbe(new SourcePolicy("","","","1@sample",root.toString()));
            SourceConfig c=sqliteConfig(); c.timeoutSeconds=1;
            try {
                long started=System.nanoTime();
                assertThatThrownBy(() -> probe.schema(c,"",1L,"samples",0)).hasMessageContaining("表字段读取失败");
                assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(1600);
                awaitWorkersIdle();
            } finally { statement.execute("ROLLBACK"); }
        }
    }
}
