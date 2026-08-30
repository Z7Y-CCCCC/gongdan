package com.heihu.workorder.service;

import com.heihu.workorder.dto.SalesOrderItemRequest;
import com.heihu.workorder.dto.SalesOrderRequest;
import com.heihu.workorder.dto.SalesOrderResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.SalesOrder;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.SalesOrderRepository;
import com.heihu.workorder.repository.UserRepository;
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
class SalesOrderServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        service = new SalesOrderService(salesOrderRepository, factoryRepository, productRepository, userRepository);
    }

    @Test
    void createNormalizesCodeAndSavesItems() {
        Factory factory = factory();
        Product product = product(10L);
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(salesOrderRepository.findByFactoryIdAndCode(1L, "SO-A")).thenReturn(Optional.empty());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> {
            SalesOrder value = invocation.getArgument(0);
            value.setId(100L);
            return value;
        });

        SalesOrderResponse response = service.create(1L, null, request(" so-a ", "DRAFT", item(10L)));

        assertThat(response.getCode()).isEqualTo("SO-A");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalQuantity()).isEqualTo(20);
    }

    @Test
    void createRejectsDuplicateCode() {
        Factory factory = factory();
        SalesOrder existing = new SalesOrder();
        existing.setId(99L);
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(salesOrderRepository.findByFactoryIdAndCode(1L, "SO-A")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, null, request("SO-A", "DRAFT", item(10L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单编码已存在：SO-A");

        verify(salesOrderRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void createRejectsInvalidStatus() {
        Factory factory = factory();
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(salesOrderRepository.findByFactoryIdAndCode(1L, "SO-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, null, request("SO-A", "BAD", item(10L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单状态不正确");

        verify(salesOrderRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void createRejectsDuplicateProductItems() {
        Factory factory = factory();
        Product product = product(10L);
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(salesOrderRepository.findByFactoryIdAndCode(1L, "SO-A")).thenReturn(Optional.empty());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.create(1L, null, request("SO-A", "DRAFT", item(10L), item(10L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单明细不能重复选择同一产品");

        verify(salesOrderRepository, never()).save(any(SalesOrder.class));
    }

    private SalesOrderRequest request(String code, String status, SalesOrderItemRequest... items) {
        SalesOrderRequest request = new SalesOrderRequest();
        request.setCode(code);
        request.setCustomerName("Test Customer");
        request.setOrderDate("2026-07-04");
        request.setDeliveryDate("2026-07-10");
        request.setStatus(status);
        for (SalesOrderItemRequest item : items) {
            request.getItems().add(item);
        }
        return request;
    }

    private SalesOrderItemRequest item(Long productId) {
        SalesOrderItemRequest request = new SalesOrderItemRequest();
        request.setProductId(productId);
        request.setQuantity(20);
        return request;
    }

    private Factory factory() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        return factory;
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setFactory(factory());
        product.setCode("P001");
        product.setName("Test Product");
        Unit unit = new Unit();
        unit.setId(1L);
        unit.setName("pcs");
        product.setUnit(unit);
        return product;
    }
}
