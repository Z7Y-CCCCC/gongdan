package com.heihu.plugins.quality;

import com.heihu.plugins.quality.QualityManagementController.QualityRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class QualityRecordRepository {

    private final JdbcTemplate jdbc;

    public QualityRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QualityRecord> list(String factoryId) {
        return jdbc.query("SELECT id, factory_id, operator_user_id, issue, status, remark, created_at "
                        + "FROM plugin_quality_record WHERE factory_id = ? ORDER BY id DESC",
                (row, number) -> {
                    QualityRecord record = new QualityRecord();
                    record.setId(row.getLong("id"));
                    record.setFactoryId(row.getString("factory_id"));
                    record.setOperatorUserId(row.getString("operator_user_id"));
                    record.setIssue(row.getString("issue"));
                    record.setStatus(row.getString("status"));
                    record.setRemark(row.getString("remark"));
                    record.setCreatedAt(row.getString("created_at"));
                    return record;
                }, factoryId);
    }

    public QualityRecord save(QualityRecord record) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO plugin_quality_record "
                            + "(factory_id, operator_user_id, issue, status, remark, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getFactoryId());
            statement.setString(2, record.getOperatorUserId());
            statement.setString(3, record.getIssue());
            statement.setString(4, record.getStatus());
            statement.setString(5, record.getRemark());
            statement.setString(6, record.getCreatedAt());
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a quality record ID");
        record.setId(id.longValue());
        return record;
    }
}
