package com.heihu.workorder.service;

import com.heihu.workorder.dto.SupplierRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Supplier;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.SupplierRepository;
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
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private FactoryRepository factoryRepository;

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(supplierRepository, factoryRepository);
    }

    @Test
    void createNormalizesCodeAndSavesSupplier() {
        Factory factory = new Factory();
        factory.setId(1L);
        when(supplierRepository.findByFactoryIdAndCode(1L, "SUP-A")).thenReturn(Optional.empty());
        when(supplierRepository.findByFactoryIdAndName(1L, "Main Supplier")).thenReturn(Optional.empty());
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        assertThat(service.create(1L, request(" sup-a ", "Main Supplier")).getCode()).isEqualTo("SUP-A");
    }

    @Test
    void createRejectsDuplicateName() {
        Factory factory = new Factory();
        factory.setId(1L);
        Supplier existing = new Supplier();
        existing.setId(2L);
        existing.setFactory(factory);
        existing.setName("Main Supplier");

        when(supplierRepository.findByFactoryIdAndCode(1L, "SUP-A")).thenReturn(Optional.empty());
        when(supplierRepository.findByFactoryIdAndName(1L, "Main Supplier")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, request("SUP-A", "Main Supplier")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商名称已存在");

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void updateRejectsSupplierFromOtherFactory() {
        Factory factory = new Factory();
        factory.setId(2L);
        Supplier supplier = new Supplier();
        supplier.setId(10L);
        supplier.setFactory(factory);
        when(supplierRepository.findById(10L)).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.update(1L, 10L, request("SUP-A", "Main Supplier")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    void deleteRemovesSupplierInFactory() {
        Factory factory = new Factory();
        factory.setId(1L);
        Supplier supplier = new Supplier();
        supplier.setId(10L);
        supplier.setFactory(factory);
        when(supplierRepository.findById(10L)).thenReturn(Optional.of(supplier));

        service.delete(1L, 10L);

        verify(supplierRepository).delete(supplier);
    }

    private SupplierRequest request(String code, String name) {
        SupplierRequest request = new SupplierRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }
}
