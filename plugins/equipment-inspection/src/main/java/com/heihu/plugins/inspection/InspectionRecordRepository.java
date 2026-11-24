package com.heihu.plugins.inspection;

import com.heihu.plugins.inspection.EquipmentInspectionController.InspectionRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InspectionRecordRepository {

    private final JdbcTemplate jdbc;

    public InspectionRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InspectionRecord> list(String factoryId) {
        return jdbc.query("SELECT id, factory_id, operator_user_id, equipment_code, inspection_result, remark, created_at "
                        + "FROM plugin_inspection_record WHERE factory_id = ? ORDER BY id DESC",
                (row, number) -> {
                    InspectionRecord record = new InspectionRecord();
                    record.setId(row.getLong("id"));
                    record.setFactoryId(row.getString("factory_id"));
                    record.setOperatorUserId(row.getString("operator_user_id"));
                    record.setEquipmentCode(row.getString("equipment_code"));
                    record.setResult(row.getString("inspection_result"));
                    record.setRemark(row.getString("remark"));
                    record.setCreatedAt(row.getString("created_at"));
                    return record;
                }, factoryId);
    }

    public InspectionRecord save(InspectionRecord record) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO plugin_inspection_record "
                            + "(factory_id, operator_user_id, equipment_code, inspection_result, remark, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getFactoryId());
            statement.setString(2, record.getOperatorUserId());
            statement.setString(3, record.getEquipmentCode());
            statement.setString(4, record.getResult());
            statement.setString(5, record.getRemark());
            statement.setString(6, record.getCreatedAt());
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Database did not return an inspection record ID");
        record.setId(id.longValue());
        return record;
    }
}
