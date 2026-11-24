package com.heihu.plugins.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class RecordPersistenceIntegrationTest {

    @TempDir
    Path storage;

    @Test
    void savedInspectionsSurviveRestartWithoutMixingFactoriesOrReusingIds() {
        Long savedId;
        try (ConfigurableApplicationContext app = start()) {
            EquipmentInspectionController controller = app.getBean(EquipmentInspectionController.class);
            savedId = controller.create("101", "11", request("EQ-PERSIST")).getBody().getId();
            assertEquals(1, controller.list("101").size());
            assertTrue(controller.list("202").isEmpty());
        }
        try (ConfigurableApplicationContext app = start()) {
            EquipmentInspectionController controller = app.getBean(EquipmentInspectionController.class);
            assertEquals(1, controller.list("101").size(), "successful saves must survive restart");
            assertEquals(savedId, controller.list("101").get(0).getId());
            assertEquals("EQ-PERSIST", controller.list("101").get(0).getEquipmentCode());
            assertEquals("11", controller.list("101").get(0).getOperatorUserId());
            assertTrue(controller.list("202").isEmpty());
            Long nextId = controller.create("202", "22", request("EQ-OTHER")).getBody().getId();
            assertTrue(nextId > savedId, "restart must not reuse an existing record ID");
            assertEquals(1, controller.list("101").size());
        }
    }

    @Test
    void simultaneousInspectionsStayUniqueAndPersistInTheirOwnFactory() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            EquipmentInspectionController controller = app.getBean(EquipmentInspectionController.class);
            ExecutorService workers = Executors.newFixedThreadPool(6);
            try {
                List<Future<Long>> writes = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    final int number = i;
                    writes.add(workers.submit(() -> controller.create(number % 2 == 0 ? "101" : "202", "11",
                            request("EQ-" + number)).getBody().getId()));
                }
                Set<Long> ids = new HashSet<>();
                for (Future<Long> write : writes) ids.add(write.get(20, TimeUnit.SECONDS));
                assertEquals(24, ids.size());
                assertEquals(12, controller.list("101").size());
                assertEquals(12, controller.list("202").size());
            } finally {
                workers.shutdownNow();
            }
        }
        try (ConfigurableApplicationContext app = start()) {
            EquipmentInspectionController controller = app.getBean(EquipmentInspectionController.class);
            assertEquals(12, controller.list("101").size());
            assertEquals(12, controller.list("202").size());
            assertTrue(controller.list("303").isEmpty());
        }
    }

    private EquipmentInspectionController.InspectionRequest request(String code) {
        EquipmentInspectionController.InspectionRequest request = new EquipmentInspectionController.InspectionRequest();
        request.setEquipmentCode(code);
        request.setResult("正常");
        request.setRemark("restart regression");
        return request;
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(EquipmentInspectionApplication.class)
                .web(WebApplicationType.NONE)
                .run("--plugin.gateway.secret=test-plugin-gateway-secret",
                        "--spring.datasource.url=jdbc:h2:file:" + storage.resolve("inspection").toAbsolutePath()
                                + ";DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }
}
