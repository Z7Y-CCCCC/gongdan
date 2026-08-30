package com.heihu.workorder.service;

import com.heihu.workorder.dto.WarehouseRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Warehouse;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private FactoryRepository factoryRepository;

    private WarehouseService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseService(warehouseRepository, factoryRepository);
    }

    @Test
    void createNormalizesCodeAndSavesWarehouse() {
        Factory factory = new Factory();
        factory.setId(1L);
        Warehouse saved = new Warehouse();
        saved.setId(10L);
        saved.setFactory(factory);
        saved.setCode("WH-A");
        saved.setName("Main Warehouse");
        saved.setEnabled(true);

        when(warehouseRepository.findByFactoryIdAndCode(1L, "WH-A")).thenReturn(Optional.empty());
        when(warehouseRepository.findByFactoryIdAndName(1L, "Main Warehouse")).thenReturn(Optional.empty());
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        WarehouseRequest request = request(" wh-a ", "Main Warehouse");
        assertThat(service.create(1L, request).getCode()).isEqualTo("WH-A");
    }

    @Test
    void createRejectsDuplicateCode() {
        Factory factory = new Factory();
        factory.setId(1L);
        Warehouse existing = new Warehouse();
        existing.setId(2L);
        existing.setFactory(factory);
        existing.setCode("WH-A");

        when(warehouseRepository.findByFactoryIdAndCode(1L, "WH-A")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, request("WH-A", "Main Warehouse")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仓库编码已存在");

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void updateRejectsWarehouseFromOtherFactory() {
        Factory factory = new Factory();
        factory.setId(2L);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setFactory(factory);
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.update(1L, 10L, request("WH-A", "Main Warehouse")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仓库不存在");
    }

    @Test
    void deleteRemovesWarehouseInFactory() {
        Factory factory = new Factory();
        factory.setId(1L);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setFactory(factory);
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));

        service.delete(1L, 10L);

        verify(warehouseRepository).delete(warehouse);
    }

    private WarehouseRequest request(String code, String name) {
        WarehouseRequest request = new WarehouseRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }
}
