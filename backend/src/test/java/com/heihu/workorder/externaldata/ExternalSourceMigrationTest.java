package com.heihu.workorder.externaldata;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class ExternalSourceMigrationTest {
    @Test void migrationCreatesConstraintsAndAllowsFactoryScopedCodes() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:h2:mem:external_migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""); Statement s=c.createStatement()) {
            s.execute("CREATE TABLE factories(id BIGINT PRIMARY KEY)");
            s.execute("INSERT INTO factories VALUES (1),(2)");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V22__create_external_data_sources.sql"));
            String insert="INSERT INTO external_data_sources(factory_id,code,name,kind,config_json,created_at,updated_at) VALUES ";
            s.execute(insert+"(1,'MES','MES','HTTP','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.execute(insert+"(2,'MES','MES','HTTP','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> s.execute(insert+"(1,'MES','MES','HTTP','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("INSERT INTO external_data_source_checks(factory_id,source_id,config_version,status,started_at,trace_id) VALUES (2,1,0,'UP',CURRENT_TIMESTAMP,'trace')")).isInstanceOf(SQLException.class);
            s.execute("INSERT INTO external_data_source_checks(factory_id,source_id,config_version,status,started_at,trace_id) VALUES (1,1,0,'UP',CURRENT_TIMESTAMP,'trace')");
        }
    }
}
