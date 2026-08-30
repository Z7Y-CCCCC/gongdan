package com.heihu.workorder.service;

import com.heihu.workorder.dto.CurrentFactoryResponse;
import com.heihu.workorder.dto.UpdateFactoryRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.FactoryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactoryServiceTest {

    @Mock
    private FactoryRepository factoryRepository;

    private FactoryService service;

    @BeforeEach
    void setUp() {
        service = new FactoryService(factoryRepository);
    }

    @Test
    void getCurrentReturnsTenantFactory() {
        Factory factory = factory();
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));

        CurrentFactoryResponse response = service.getCurrent(1L);

        assertThat(response.getCode()).isEqualTo("HHGD");
        assertThat(response.getName()).isEqualTo("Old Name");
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void updateCurrentTrimsAndSavesName() {
        Factory factory = factory();
        UpdateFactoryRequest request = new UpdateFactoryRequest();
        request.setName(" New Name ");
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(factoryRepository.save(factory)).thenReturn(factory);

        CurrentFactoryResponse response = service.updateCurrent(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        verify(factoryRepository).save(factory);
    }

    private Factory factory() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Old Name");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        return factory;
    }
}
