package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.ProcessRoute;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProcessRouteRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
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
class ProcessRouteServiceTest {

    @Mock
    private ProcessRouteRepository routeRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    private ProcessRouteService service;

    @BeforeEach
    void setUp() {
        service = new ProcessRouteService(routeRepository, factoryRepository, userRepository, productRepository);
    }

    @Test
    void deleteRejectsRouteReferencedByProduct() {
        ProcessRoute route = route();
        when(routeRepository.findById(20L)).thenReturn(Optional.of(route));
        when(productRepository.existsByFactoryIdAndProcessRouteId(1L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线已被产品引用，不能删除");

        verify(routeRepository, never()).delete(route);
    }

    @Test
    void deleteRemovesUnreferencedRoute() {
        ProcessRoute route = route();
        when(routeRepository.findById(20L)).thenReturn(Optional.of(route));

        service.delete(1L, 20L);

        verify(routeRepository).delete(route);
    }

    private ProcessRoute route() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        ProcessRoute route = new ProcessRoute();
        route.setId(20L);
        route.setFactory(factory);
        route.setCode("R001");
        route.setName("Main Route");
        route.setCreatedAt(LocalDateTime.now());
        return route;
    }
}
