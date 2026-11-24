package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailPageResponse;
import com.heihu.workorder.dto.InventoryDetailResponse;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class InventoryDetailService {
    private static final int MAX_PAGE_SIZE = 500;
    private final NamedParameterJdbcTemplate jdbc;

    public InventoryDetailService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(cacheNames = "inventory.details",
            key = "T(com.heihu.workorder.service.InventoryDetailService).cacheKey(#root.args)")
    public InventoryDetailPageResponse list(Long factoryId, String receiptCode, String receiptType,
                                            String sourceType, String remark, String productCode,
                                            String productName, String productSpec, String productAttribute,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            int page, int size) {
        int limit = Math.min(MAX_PAGE_SIZE, Math.max(size, 1));
        long offset = (Math.max(page, 1) - 1L) * limit;
        MapSqlParameterSource params = new MapSqlParameterSource("factoryId", factoryId)
                .addValue("limit", limit).addValue("offset", offset);
        StringBuilder filters = new StringBuilder(" WHERE r.factory_id = :factoryId");
        textFilter(filters, params, "r.code", "receiptCode", receiptCode, false);
        textFilter(filters, params, "r.receipt_type", "receiptType", receiptType, true);
        textFilter(filters, params, "r.remark", "remark", remark, false);
        boolean needsProduct = appendProductFilters(filters, params, productCode, productName, productSpec, productAttribute);
        if (startTime != null) {
            filters.append(" AND r.operation_time >= :startTime");
            params.addValue("startTime", Timestamp.valueOf(startTime), Types.TIMESTAMP);
        }
        if (endTime != null) {
            filters.append(" AND r.operation_time <= :endTime");
            params.addValue("endTime", Timestamp.valueOf(endTime), Types.TIMESTAMP);
        }

        String source = normalize(sourceType);
        List<String> counts = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        if (source == null || "in".equals(source)) {
            addQueries(counts, rows, filters.toString(), needsProduct, "inbound", "IN");
        }
        if (source == null || "out".equals(source)) {
            addQueries(counts, rows, filters.toString(), needsProduct, "outbound", "OUT");
        }
        if (counts.isEmpty()) return new InventoryDetailPageResponse(new ArrayList<>(), 0);
        long total = jdbc.queryForObject("SELECT SUM(line_count) FROM (" +
                String.join(" UNION ALL ", counts) + ") counts", params, Long.class);
        if (offset >= total) return new InventoryDetailPageResponse(new ArrayList<>(), total);

        List<InventoryDetailResponse> items = jdbc.query(
                String.join(" UNION ALL ", rows) +
                        " ORDER BY operation_time DESC, source_type ASC, item_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> {
                    // Hibernate 5 persists LocalDateTime through JDBC Timestamp. Use the same
                    // driver conversion in both directions, rather than mixing JDBC 4.2's
                    // timezone-less LocalDateTime binding with the JPA-written values.
                    Timestamp time = rs.getTimestamp("operation_time");
                    return new InventoryDetailResponse(rs.getString("source_type"), rs.getString("receipt_code"),
                            rs.getString("receipt_type"), time == null ? null : time.toLocalDateTime(),
                            rs.getString("remark"), rs.getString("product_code"), rs.getString("product_name"),
                            rs.getString("spec"), rs.getString("attribute"), rs.getString("unit_name"),
                            rs.getInt("quantity"), rs.getInt("change_qty"));
                });
        return new InventoryDetailPageResponse(items, total);
    }

    private static void addQueries(List<String> counts, List<String> rows, String filters,
                                   boolean needsProduct, String direction, String source) {
        // direction/source come only from the two fixed branches above; filter values remain bound.
        String time = "r." + direction + "_time";
        String type = "r." + direction + "_type";
        String where = filters.replace("r.operation_time", time).replace("r.receipt_type", type);
        String from = " FROM " + direction + "_receipts r JOIN " + direction + "_receipt_items i ON i."
                + direction + "_receipt_id = r.id";
        String product = " LEFT JOIN products p ON p.id = i.product_id AND p.factory_id = r.factory_id";
        counts.add("SELECT COUNT(*) AS line_count" + from + (needsProduct ? product : "") + where);
        rows.add("SELECT '" + source + "' AS source_type, i.id AS item_id, r.code AS receipt_code, "
                + type + " AS receipt_type, " + time + " AS operation_time, r.remark, "
                + "p.code AS product_code, p.name AS product_name, p.spec, p.attribute, u.name AS unit_name, "
                + "i.quantity, " + ("IN".equals(source) ? "i.quantity" : "-i.quantity") + " AS change_qty"
                + from + product + " LEFT JOIN units u ON u.id = p.unit_id AND u.factory_id = r.factory_id" + where);
    }

    private static boolean appendProductFilters(StringBuilder sql, MapSqlParameterSource params, String code,
                                            String name, String spec, String attribute) {
        int start = sql.length();
        textFilter(sql, params, "p.code", "productCode", code, false);
        textFilter(sql, params, "p.name", "productName", name, false);
        textFilter(sql, params, "p.spec", "productSpec", spec, false);
        textFilter(sql, params, "p.attribute", "productAttribute", attribute, true);
        return sql.length() != start;
    }

    private static void textFilter(StringBuilder sql, MapSqlParameterSource params, String column,
                                      String key, String value, boolean exact) {
        String normalized = normalize(value);
        if (normalized == null) return;
        String field = "LOWER(TRIM(" + column + "))";
        sql.append(exact ? " AND " + field + " = :" + key : " AND LOCATE(:" + key + ", " + field + ") > 0");
        params.addValue(key, normalized);
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String cacheKey(Object[] args) {
        StringBuilder key = new StringBuilder("page-v3:");
        for (Object arg : args) {
            String value = arg instanceof String ? normalize((String) arg) : arg == null ? null : arg.toString();
            if (value == null) key.append("-:");
            else key.append(value.length()).append(':').append(value);
        }
        return key.toString();
    }
}
