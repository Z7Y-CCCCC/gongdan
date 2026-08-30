package com.heihu.workorder.service;

import com.heihu.workorder.dto.BomItemRequest;
import com.heihu.workorder.dto.BomRequest;
import com.heihu.workorder.dto.BomResponse;
import com.heihu.workorder.model.BillOfMaterial;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.BomRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @Mock
    private BomRepository bomRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private ProductRepository productRepository;

    private BomService service;

    @BeforeEach
    void setUp() {
        service = new BomService(bomRepository, factoryRepository, productRepository);
    }

    @Test
    void createNormalizesCodeAndSavesItems() {
        Factory factory = factory();
        Product header = product(10L, "P-FG", "Finished");
        Product item = product(11L, "P-RM", "Raw");

        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(productRepository.findById(10L)).thenReturn(Optional.of(header));
        when(productRepository.findById(11L)).thenReturn(Optional.of(item));
        when(bomRepository.findByFactoryIdAndCode(1L, "BOM-A")).thenReturn(Optional.empty());
        when(bomRepository.findByFactoryIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(bomRepository.save(any(BillOfMaterial.class))).thenAnswer(invocation -> {
            BillOfMaterial value = invocation.getArgument(0);
            value.setId(100L);
            return value;
        });

        BomRequest request = request(" bom-a ", 10L, itemRequest(11L));

        BomResponse response = service.create(1L, request);

        assertThat(response.getCode()).isEqualTo("BOM-A");
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void createRejectsDuplicateProductBom() {
        Factory factory = factory();
        Product header = product(10L, "P-FG", "Finished");
        BillOfMaterial existing = new BillOfMaterial();
        existing.setId(99L);

        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(productRepository.findById(10L)).thenReturn(Optional.of(header));
        when(bomRepository.findByFactoryIdAndCode(1L, "BOM-A")).thenReturn(Optional.empty());
        when(bomRepository.findByFactoryIdAndProductId(1L, 10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, request("BOM-A", 10L, itemRequest(11L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该成品已存在BOM");

        verify(bomRepository, never()).save(any(BillOfMaterial.class));
    }

    @Test
    void createRejectsItemSameAsHeaderProduct() {
        Factory factory = factory();
        Product header = product(10L, "P-FG", "Finished");

        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(productRepository.findById(10L)).thenReturn(Optional.of(header));
        when(bomRepository.findByFactoryIdAndCode(1L, "BOM-A")).thenReturn(Optional.empty());
        when(bomRepository.findByFactoryIdAndProductId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request("BOM-A", 10L, itemRequest(10L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("子件不能和成品相同");

        verify(bomRepository, never()).save(any(BillOfMaterial.class));
    }

    @Test
    void createRejectsInvalidScrapRate() {
        Factory factory = factory();
        Product header = product(10L, "P-FG", "Finished");
        Product item = product(11L, "P-RM", "Raw");
        BomItemRequest itemRequest = itemRequest(11L);
        itemRequest.setScrapRate(new BigDecimal("101"));

        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(productRepository.findById(10L)).thenReturn(Optional.of(header));
        when(productRepository.findById(11L)).thenReturn(Optional.of(item));
        when(bomRepository.findByFactoryIdAndCode(1L, "BOM-A")).thenReturn(Optional.empty());
        when(bomRepository.findByFactoryIdAndProductId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request("BOM-A", 10L, itemRequest)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("损耗率必须在0到100之间");

        verify(bomRepository, never()).save(any(BillOfMaterial.class));
    }

    private BomRequest request(String code, Long productId, BomItemRequest item) {
        BomRequest request = new BomRequest();
        request.setCode(code);
        request.setProductId(productId);
        request.setOutputQuantity(BigDecimal.ONE);
        request.getItems().add(item);
        return request;
    }

    private BomItemRequest itemRequest(Long productId) {
        BomItemRequest request = new BomItemRequest();
        request.setProductId(productId);
        request.setQuantity(new BigDecimal("2.5"));
        request.setScrapRate(BigDecimal.ZERO);
        return request;
    }

    private Factory factory() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        return factory;
    }

    private Product product(Long id, String code, String name) {
        Product product = new Product();
        product.setId(id);
        product.setFactory(factory());
        product.setCode(code);
        product.setName(name);
        Unit unit = new Unit();
        unit.setId(1L);
        unit.setName("pcs");
        product.setUnit(unit);
        return product;
    }
}
