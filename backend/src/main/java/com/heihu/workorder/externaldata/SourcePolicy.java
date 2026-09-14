package com.heihu.workorder.externaldata;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Deployment-owned, factory-scoped allowlists. No wildcard, proxy, redirect or arbitrary JDBC URL. */
@Component
public class SourcePolicy {
    private final Set<String> targets, addresses, plaintext, files;
    private final String sqliteRoot;
    public SourcePolicy(@Value("${app.external-data.allowed-targets:}") String targets,
                        @Value("${app.external-data.allowed-addresses:}") String addresses,
                        @Value("${app.external-data.plaintext-targets:}") String plaintext,
                        @Value("${app.external-data.sqlite-files:}") String files,
                        @Value("${app.external-data.sqlite-root:}") String sqliteRoot) {
        this.targets = entries(targets); this.addresses = entries(addresses);
        this.plaintext = entries(plaintext); this.files = entries(files); this.sqliteRoot = sqliteRoot;
    }
    private Set<String> entries(String value) {
        Set<String> result = new HashSet<>();
        for (String s : value.split(",")) if (!s.trim().isEmpty()) result.add(s.trim().toLowerCase(Locale.ROOT));
        return result;
    }
    public List<String> fileIds(Long factoryId) {
        List<String> result = new ArrayList<>();
        for (String s : files) if (s.startsWith(factoryId + "@")) result.add(s.substring(s.indexOf('@') + 1));
        Collections.sort(result); return result;
    }
    public void validate(SourceConfig c, Long factoryId) {
        if (!Arrays.asList("HTTP", "DATABASE").contains(c.kind) ||
            !Arrays.asList("KEEP", "REPLACE", "CLEAR").contains(c.secretAction)) fail("数据源类型或凭据操作无效");
        if (c.timeoutSeconds < 1 || c.timeoutSeconds > 30) fail("超时必须为 1–30 秒");
        if ("HTTP".equals(c.kind)) {
            if (!Arrays.asList("NONE", "API_KEY", "BEARER", "BASIC").contains(c.authType)) fail("认证方式无效");
            URI uri = healthUri(c, false);
            allowTarget(factoryId, uri.getHost(), port(uri), "https".equals(uri.getScheme()));
            if ("API_KEY".equals(c.authType)) {
                if (c.headerName == null || !c.headerName.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) fail("API Key 请求头名称无效");
                String h = c.headerName.toLowerCase(Locale.ROOT);
                if (Arrays.asList("host", "authorization", "cookie", "connection", "content-length", "transfer-encoding",
                    "proxy-authorization", "forwarded", "accept-encoding", "upgrade", "te", "trailer").contains(h)
                    || h.startsWith("x-forwarded-") || h.startsWith("sec-") || h.startsWith("proxy-")) fail("不能使用保留请求头");
            }
            if ("BASIC".equals(c.authType) && (blank(c.username) || c.username.contains(":"))) fail("Basic 用户名不能为空或包含冒号");
        } else {
            if (!Arrays.asList("MYSQL", "POSTGRESQL", "SQLSERVER", "SQLITE").contains(c.driver)) fail("数据库类型无效");
            if ("SQLITE".equals(c.driver)) {
                if (c.fileId == null || !c.fileId.matches("[a-z0-9_-]{1,64}") || !files.contains(factoryId + "@" + c.fileId)) fail("SQLite 文件未由运维登记");
            } else {
                // Numeric IPv4 avoids driver-specific DNS rebinding/secondary-host behaviour.
                if (!ipv4(c.host)) fail("一期数据库主机请填写固定 IPv4 地址，不接受域名或连接串");
                if (blank(c.username)) fail("请填写只读数据库用户名");
                if (c.database == null || !c.database.matches("[A-Za-z0-9_-]{1,128}")) fail("数据库名仅支持字母、数字、下划线和连字符");
                if (!blank(c.schema) && !c.schema.matches("[A-Za-z0-9_-]{1,128}")) fail("Schema 名称格式无效");
                if (("POSTGRESQL".equals(c.driver) || "SQLSERVER".equals(c.driver)) && blank(c.schema)) fail("请指定一个允许浏览的 Schema");
                allowTarget(factoryId, c.host, c.port, c.tls);
            }
        }
        if (c.secretValue == null || c.secretValue.indexOf('\r') >= 0 || c.secretValue.indexOf('\n') >= 0 || c.secretValue.indexOf('\0') >= 0)
            fail("凭据不能包含控制换行字符");
    }
    public boolean needsSecret(SourceConfig c) {
        return "DATABASE".equals(c.kind) ? !"SQLITE".equals(c.driver) : !"NONE".equals(c.authType);
    }
    public URI healthUri(SourceConfig c, boolean requireHealth) {
        try {
            if (blank(c.baseUrl)) throw new IllegalArgumentException();
            URI base = new URI(c.baseUrl.trim());
            if (!Arrays.asList("http", "https").contains(base.getScheme()) || base.getHost() == null ||
                base.getUserInfo() != null || base.getRawQuery() != null || base.getFragment() != null ||
                base.getPort() == 0 || base.getPort() > 65535) throw new IllegalArgumentException();
            safePath(base.getRawPath());
            String path = c.healthPath == null ? "" : c.healthPath.trim();
            if (requireHealth && path.isEmpty()) fail("未配置健康检查路径");
            if (!path.isEmpty() && !path.startsWith("/")) fail("健康检查路径必须以 / 开头");
            safePath(path);
            String root = base.toASCIIString().replaceAll("/+$", "");
            return new URI(root + path);
        } catch (URISyntaxException e) { throw new IllegalArgumentException("API 地址格式无效"); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("API 地址或健康路径无效；不支持查询串、重定向和路径穿越"); }
    }
    private void safePath(String p) {
        if (p == null || p.isEmpty()) return;
        if (!p.matches("[/A-Za-z0-9_.~-]*") || p.contains("//")) fail("路径格式无效");
        for (String part : p.split("/")) if (".".equals(part) || "..".equals(part)) fail("禁止路径穿越");
    }
    public int port(URI uri) { return uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort(); }
    private void allowTarget(Long factory, String host, int port, boolean tls) {
        String key = factory + "@" + host.toLowerCase(Locale.ROOT) + ":" + port;
        if (port < 1 || port > 65535 || !targets.contains(key)) fail("目标地址未获当前工厂出口授权，请联系运维");
        if (!tls && !plaintext.contains(key)) fail("默认要求 TLS；明文连接需运维单独授权");
    }
    public InetAddress[] resolve(Long factory, String host, int port, boolean tls) throws UnknownHostException {
        allowTarget(factory, host, port, tls);
        InetAddress[] resolved = InetAddress.getAllByName(host);
        if (resolved.length == 0) throw new UnknownHostException();
        for (InetAddress a : resolved) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()
                || a.getHostAddress().equals("168.63.129.16")
                || !addresses.contains(factory + "@" + a.getHostAddress().toLowerCase(Locale.ROOT)))
                fail("目标解析地址未获授权或属于禁止访问的网络");
        }
        return resolved;
    }
    public Path sqliteFile(Long factory, String fileId) throws IOException {
        if (blank(sqliteRoot) || fileId == null || !fileId.matches("[a-z0-9_-]{1,64}") || !fileIds(factory).contains(fileId))
            fail("SQLite 文件未由运维登记");
        Path configuredRoot = Paths.get(sqliteRoot);
        if (!configuredRoot.isAbsolute() || sqliteRoot.startsWith("\\\\") || sqliteRoot.startsWith("//"))
            fail("SQLite 专用目录必须为后端本机绝对路径，不支持 UNC");
        Path root = configuredRoot.toRealPath();
        if (!Files.isDirectory(root)) fail("SQLite 专用目录无效");
        Path file = root.resolve(fileId + ".db").toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || !Files.isReadable(file)) fail("SQLite 文件不可读或越界");
        return file;
    }
    static boolean blank(String v) { return v == null || v.trim().isEmpty(); }
    static boolean ipv4(String host) {
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        for (String p : host.split("\\.")) if (Integer.parseInt(p) > 255 || (p.length() > 1 && p.startsWith("0"))) return false;
        return true;
    }
    private void fail(String message) { throw new IllegalArgumentException(message); }
}
