package com.heihu.workorder.service;

import com.heihu.workorder.dto.BomItemRequest;
import com.heihu.workorder.dto.BomRequest;
import com.heihu.workorder.dto.BomResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.BomRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BomNumericIntegrityIntegrationTest {
    @Autowired private BomService service;
    @Autowired private BomRepository boms;
    @Autowired private FactoryRepository factories;
    @Autowired private UnitRepository units;
    @Autowired private ProductRepository products;

    private Factory factory;
    private Product header;
    private Product component;

    @BeforeEach
    void setUp() {
        factory = new Factory();
        factory.setCode("NUM" + UUID.randomUUID().toString().substring(0, 8));
        factory.setName(factory.getCode());
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        factory = factories.saveAndFlush(factory);
        Unit unit = new Unit();
        unit.setFactory(factory);
        unit.setName("pcs");
        unit.setCreatedAt(LocalDateTime.now());
        unit = units.saveAndFlush(unit);
        header = product("FG", unit);
        component = product("RM", unit);
    }

    @Test
    void rejectsPositiveOutputThatWouldBeStoredAsZero() {
        BomRequest request = request();
        request.setOutputQuantity(new BigDecimal("0.00001"));
        assertThatThrownBy(() -> service.create(factory.getId(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("产出数量");
        assertThat(boms.search(factory.getId(), "", "", Pageable.unpaged())).isEmpty();
    }

    @Test
    void rejectsComponentQuantityThatWouldBeSilentlyRounded() {
        BomRequest request = request();
        request.getItems().get(0).setQuantity(new BigDecimal("1.23456"));
        assertThatThrownBy(() -> service.create(factory.getId(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("子件用量");
    }

    @Test
    void rejectsOutputOutsideDatabaseRangeWithFieldError() {
        BomRequest request = request();
        request.setOutputQuantity(new BigDecimal("100000000000000"));
        assertThatThrownBy(() -> service.create(factory.getId(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("产出数量");
    }

    @Test
    void rejectsScrapRateThatWouldBeSilentlyRounded() {
        BomRequest request = request();
        request.getItems().get(0).setScrapRate(new BigDecimal("12.34567"));
        assertThatThrownBy(() -> service.create(factory.getId(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("损耗率");
    }

    @Test
    void validBoundaryAndTrailingZerosRoundTripExactly() {
        BomRequest request = request();
        request.setOutputQuantity(new BigDecimal("99999999999999.999900"));
        request.getItems().get(0).setQuantity(new BigDecimal("0.000100"));
        request.getItems().get(0).setScrapRate(new BigDecimal("100.00000"));
        BomResponse created = service.create(factory.getId(), request);
        BomResponse loaded = service.list(factory.getId(), "", "", PageRequest.of(0, 20)).getContent().get(0);
        assertThat(loaded.getId()).isEqualTo(created.getId());
        assertThat(loaded.getOutputQuantity()).isEqualByComparingTo(request.getOutputQuantity());
        assertThat(loaded.getItems().get(0).getQuantity()).isEqualByComparingTo("0.0001");
        assertThat(loaded.getItems().get(0).getScrapRate()).isEqualByComparingTo("100");
    }

    @Test
    void invalidUpdatePreservesExistingHeaderAndItems() {
        BomRequest original = request();
        original.setRemark("original");
        BomResponse created = service.create(factory.getId(), original);
        BomRequest invalid = request();
        invalid.setRemark("must not persist");
        invalid.getItems().get(0).setQuantity(new BigDecimal("0.000001"));
        assertThatThrownBy(() -> service.update(factory.getId(), created.getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("子件用量");
        BomResponse loaded = service.list(factory.getId(), "", "", PageRequest.of(0, 20)).getContent().get(0);
        assertThat(loaded.getRemark()).isEqualTo("original");
        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private Product product(String code, Unit unit) {
        Product product = new Product();
        product.setFactory(factory);
        product.setUnit(unit);
        product.setCode(code);
        product.setName(code);
        product.setAttribute("自制");
        product.setCreatedAt(LocalDateTime.now());
        return products.saveAndFlush(product);
    }

    private BomRequest request() {
        BomRequest request = new BomRequest();
        request.setProductId(header.getId());
        request.setOutputQuantity(BigDecimal.ONE);
        BomItemRequest item = new BomItemRequest();
        item.setProductId(component.getId());
        item.setQuantity(BigDecimal.ONE);
        request.getItems().add(item);
        return request;
    }
}
