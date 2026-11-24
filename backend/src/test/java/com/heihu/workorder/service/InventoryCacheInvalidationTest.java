package com.heihu.workorder.service;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCacheInvalidationTest {
    @Test
    void everyInventoryMutationInvalidatesAllDependentViews() {
        for (Class<?> type : Arrays.asList(ProductService.class, InboundReceiptService.class, OutboundReceiptService.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Arrays.asList("create", "update", "delete").contains(method.getName())) continue;
                CacheEvict eviction = method.getAnnotation(CacheEvict.class);
                assertThat(eviction).as(type.getSimpleName() + "." + method.getName()).isNotNull();
                assertThat(eviction.allEntries()).isTrue();
                assertThat(eviction.cacheNames()).contains("products.list", "inventory.details",
                        "inbound.receipts.list", "inbound.receipts.detail",
                        "outbound.receipts.list", "outbound.receipts.detail");
            }
        }
    }

    @Test
    void renamingAUnitInvalidatesReceiptAndInventoryViews() throws NoSuchMethodException {
        CacheEvict eviction = UnitService.class.getMethod("update", Long.class, Long.class,
                com.heihu.workorder.dto.UnitRequest.class).getAnnotation(CacheEvict.class);
        assertThat(eviction.allEntries()).isTrue();
        assertThat(eviction.cacheNames()).contains("inventory.details", "inbound.receipts.list",
                "inbound.receipts.detail", "outbound.receipts.list", "outbound.receipts.detail",
                "workorders.list", "workorders.detail", "workorders.code", "sales.orders.list", "boms.list");
    }
}
