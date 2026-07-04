package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UnitRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private ProductRepository productRepository;

    private UnitService service;

    @BeforeEach
    void setUp() {
        service = new UnitService(unitRepository, factoryRepository, productRepository);
    }

    @Test
    void deleteRejectsUnitReferencedByProduct() {
        Unit unit = unit();
        when(unitRepository.findById(10L)).thenReturn(Optional.of(unit));
        when(productRepository.existsByFactoryIdAndUnitId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单位已被产品引用，不能删除");

        verify(unitRepository, never()).delete(unit);
    }

    @Test
    void deleteRemovesUnreferencedUnit() {
        Unit unit = unit();
        when(unitRepository.findById(10L)).thenReturn(Optional.of(unit));

        service.delete(1L, 10L);

        verify(unitRepository).delete(unit);
    }

    private Unit unit() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        Unit unit = new Unit();
        unit.setId(10L);
        unit.setFactory(factory);
        unit.setName("pcs");
        unit.setCreatedAt(LocalDateTime.now());
        return unit;
    }
}
