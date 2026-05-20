package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailResponse;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryDetailService {

    @PersistenceContext
    private EntityManager entityManager;

    public long count(Long factoryId, String receiptCode, String receiptType,
                      String sourceType, String remark, String productCode,
                      String productName, String productSpec, String productAttribute,
                      LocalDateTime startTime, LocalDateTime endTime) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM (");
        sql.append(buildUnionSql());
        sql.append(") AS t WHERE 1=1");

        List<Object> params = new ArrayList<>();
        int paramIndex = buildWhereClause(sql, params, factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                startTime, endTime, 1);

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        Object result = query.getSingleResult();
        if (result instanceof BigInteger) {
            return ((BigInteger) result).longValue();
        }
        return ((Number) result).longValue();
    }

    public List<InventoryDetailResponse> list(Long factoryId, String receiptCode, String receiptType,
                                               String sourceType, String remark, String productCode,
                                               String productName, String productSpec, String productAttribute,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int page, int size) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.source_type, t.receipt_code, t.receipt_type, t.operation_time, ");
        sql.append("t.remark, t.product_code, t.product_name, t.product_spec, ");
        sql.append("t.product_attribute, t.unit_name, t.receipt_qty, t.change_qty ");
        sql.append("FROM (");
        sql.append(buildUnionSql());
        sql.append(") AS t WHERE 1=1");

        List<Object> params = new ArrayList<>();
        int paramIndex = buildWhereClause(sql, params, factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                startTime, endTime, 1);

        sql.append(" ORDER BY t.operation_time DESC");
        sql.append(" LIMIT ?").append(paramIndex).append(" OFFSET ?").append(paramIndex + 1);
        params.add(size);
        params.add((page - 1) * size);

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<InventoryDetailResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            InventoryDetailResponse dto = new InventoryDetailResponse();
            dto.setSourceType((String) row[0]);
            dto.setReceiptCode((String) row[1]);
            dto.setReceiptType((String) row[2]);
            if (row[3] instanceof Timestamp) {
                dto.setOperationTime(((Timestamp) row[3]).toLocalDateTime());
            } else if (row[3] instanceof LocalDateTime) {
                dto.setOperationTime((LocalDateTime) row[3]);
            }
            dto.setRemark((String) row[4]);
            dto.setProductCode((String) row[5]);
            dto.setProductName((String) row[6]);
            dto.setProductSpec((String) row[7]);
            dto.setProductAttribute((String) row[8]);
            dto.setUnitName((String) row[9]);
            dto.setReceiptQty(row[10] != null ? ((Number) row[10]).intValue() : 0);
            dto.setChangeQty(row[11] != null ? ((Number) row[11]).intValue() : 0);
            result.add(dto);
        }
        return result;
    }

    private String buildUnionSql() {
        StringBuilder sql = new StringBuilder();

        // Inbound part: positive change_qty
        sql.append("SELECT 'IN' AS source_type, ");
        sql.append("r.code AS receipt_code, ");
        sql.append("r.inbound_type AS receipt_type, ");
        sql.append("r.inbound_time AS operation_time, ");
        sql.append("r.remark AS remark, ");
        sql.append("p.code AS product_code, ");
        sql.append("p.name AS product_name, ");
        sql.append("p.spec AS product_spec, ");
        sql.append("p.attribute AS product_attribute, ");
        sql.append("u.name AS unit_name, ");
        sql.append("i.quantity AS receipt_qty, ");
        sql.append("i.quantity AS change_qty, ");
        sql.append("r.factory_id AS factory_id ");
        sql.append("FROM inbound_receipt_items i ");
        sql.append("JOIN inbound_receipts r ON i.inbound_receipt_id = r.id ");
        sql.append("JOIN products p ON i.product_id = p.id ");
        sql.append("JOIN units u ON p.unit_id = u.id");

        sql.append(" UNION ALL ");

        // Outbound part: negative change_qty
        sql.append("SELECT 'OUT' AS source_type, ");
        sql.append("r.code AS receipt_code, ");
        sql.append("r.outbound_type AS receipt_type, ");
        sql.append("r.outbound_time AS operation_time, ");
        sql.append("r.remark AS remark, ");
        sql.append("p.code AS product_code, ");
        sql.append("p.name AS product_name, ");
        sql.append("p.spec AS product_spec, ");
        sql.append("p.attribute AS product_attribute, ");
        sql.append("u.name AS unit_name, ");
        sql.append("i.quantity AS receipt_qty, ");
        sql.append("(0 - i.quantity) AS change_qty, ");
        sql.append("r.factory_id AS factory_id ");
        sql.append("FROM outbound_receipt_items i ");
        sql.append("JOIN outbound_receipts r ON i.outbound_receipt_id = r.id ");
        sql.append("JOIN products p ON i.product_id = p.id ");
        sql.append("JOIN units u ON p.unit_id = u.id");

        return sql.toString();
    }

    private int buildWhereClause(StringBuilder sql, List<Object> params,
                                  Long factoryId, String receiptCode, String receiptType,
                                  String sourceType, String remark, String productCode,
                                  String productName, String productSpec, String productAttribute,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  int paramIndex) {

        // Factory filter is mandatory
        sql.append(" AND t.factory_id = ?").append(paramIndex);
        params.add(factoryId);
        paramIndex++;

        if (receiptCode != null && !receiptCode.trim().isEmpty()) {
            sql.append(" AND t.receipt_code LIKE ?").append(paramIndex);
            params.add("%" + receiptCode.trim() + "%");
            paramIndex++;
        }

        if (receiptType != null && !receiptType.trim().isEmpty()) {
            sql.append(" AND t.receipt_type = ?").append(paramIndex);
            params.add(receiptType.trim());
            paramIndex++;
        }

        if (sourceType != null && !sourceType.trim().isEmpty()) {
            sql.append(" AND t.source_type = ?").append(paramIndex);
            params.add(sourceType.trim());
            paramIndex++;
        }

        if (remark != null && !remark.trim().isEmpty()) {
            sql.append(" AND t.remark LIKE ?").append(paramIndex);
            params.add("%" + remark.trim() + "%");
            paramIndex++;
        }

        if (productCode != null && !productCode.trim().isEmpty()) {
            sql.append(" AND t.product_code LIKE ?").append(paramIndex);
            params.add("%" + productCode.trim() + "%");
            paramIndex++;
        }

        if (productName != null && !productName.trim().isEmpty()) {
            sql.append(" AND t.product_name LIKE ?").append(paramIndex);
            params.add("%" + productName.trim() + "%");
            paramIndex++;
        }

        if (productSpec != null && !productSpec.trim().isEmpty()) {
            sql.append(" AND t.product_spec LIKE ?").append(paramIndex);
            params.add("%" + productSpec.trim() + "%");
            paramIndex++;
        }

        if (productAttribute != null && !productAttribute.trim().isEmpty()) {
            sql.append(" AND t.product_attribute = ?").append(paramIndex);
            params.add(productAttribute.trim());
            paramIndex++;
        }

        if (startTime != null) {
            sql.append(" AND t.operation_time >= ?").append(paramIndex);
            params.add(Timestamp.valueOf(startTime));
            paramIndex++;
        }

        if (endTime != null) {
            sql.append(" AND t.operation_time <= ?").append(paramIndex);
            params.add(Timestamp.valueOf(endTime));
            paramIndex++;
        }

        return paramIndex;
    }
}
