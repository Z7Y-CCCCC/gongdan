package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.ProductOperationLog;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.dto.ProductRequest;
import com.heihu.workorder.dto.ProductResponse;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.BomRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import com.heihu.workorder.repository.ProcessRouteRepository;
import com.heihu.workorder.repository.ProductOperationLogRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.SalesOrderRepository;
import com.heihu.workorder.repository.UnitRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOperationLogRepository productOperationLogRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private ProcessRouteRepository routeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private InboundReceiptRepository inboundReceiptRepository;

    @Mock
    private OutboundReceiptRepository outboundReceiptRepository;

    @Mock
    private BomRepository bomRepository;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                productOperationLogRepository,
                factoryRepository,
                unitRepository,
                routeRepository,
                userRepository,
                workOrderRepository,
                inboundReceiptRepository,
                outboundReceiptRepository,
                bomRepository,
                salesOrderRepository
        );
    }

    @Test
    void deleteRejectsProductReferencedByWorkOrder() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(workOrderRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被工单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByInboundReceipt() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(inboundReceiptRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被入库单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByOutboundReceipt() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(outboundReceiptRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被出库单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByBom() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(bomRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被BOM引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedBySalesOrder() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.existsItemByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被销售订单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRemovesProductAndWritesLogWhenUnreferenced() {
        Product product = product();
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));

        service.delete(1L, "operator", 10L);

        verify(productRepository).delete(product);
        ArgumentCaptor<ProductOperationLog> logCaptor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(productOperationLogRepository).save(logCaptor.capture());
        ProductOperationLog log = logCaptor.getValue();
        assertThat(log.getActionType()).isEqualTo("DELETE");
        assertThat(log.getProductCode()).isEqualTo("P001");
        assertThat(log.getProductName()).isEqualTo("Test Product");
    }

    @Test
    void metadataUpdatePreservesInventoryWhenQuantityIsNotSupplied() {
        Product product = product();
        product.setInventoryQty(37);
        product.setVersion(5L);
        Unit unit = new Unit();
        unit.setId(20L);
        unit.setFactory(product.getFactory());
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(unitRepository.findById(20L)).thenReturn(Optional.of(unit));
        when(productRepository.saveAndFlush(product)).thenAnswer(invocation -> {
            product.setVersion(6L);
            return product;
        });
        ProductRequest request = validRequest();
        request.setVersion(5L);

        ProductResponse response = service.update(1L, 2L, "operator", 10L, request);

        assertThat(product.getInventoryQty()).isEqualTo(37);
        assertThat(response.getVersion()).isEqualTo(6L);
        verify(productRepository, never()).findById(10L);
    }

    @Test
    void staleProductVersionCannotOverwriteChangedStock() {
        Product product = product();
        product.setInventoryQty(37);
        product.setVersion(5L);
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        ProductRequest request = validRequest();
        request.setVersion(4L);
        request.setInventoryQty(3);

        assertThatThrownBy(() -> service.update(1L, 2L, "operator", 10L, request))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(product.getInventoryQty()).isEqualTo(37);
        assertThat(product.getVersion()).isEqualTo(5L);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void omittingVersionCannotBypassInventoryConflictProtection() {
        Product product = product();
        product.setInventoryQty(37);
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        ProductRequest request = validRequest();
        request.setInventoryQty(0);
        assertThatThrownBy(() -> service.update(1L, 2L, "operator", 10L, request))
                .isInstanceOf(com.heihu.workorder.exception.ApiException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(product.getInventoryQty()).isEqualTo(37);
    }

    @Test
    void negativeProductInventoryIsRejected() {
        Product product = product();
        Unit unit = new Unit();
        unit.setId(20L);
        unit.setFactory(product.getFactory());
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(unitRepository.findById(20L)).thenReturn(Optional.of(unit));
        ProductRequest request = validRequest();
        request.setInventoryQty(-1);
        assertThatThrownBy(() -> service.update(1L, 2L, "operator", 10L, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("库存数量");
    }

    private ProductRequest validRequest() {
        ProductRequest request = new ProductRequest();
        request.setCode("P001");
        request.setName("Updated Product");
        request.setUnitId(20L);
        return request;
    }

    private Product product() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());

        Product product = new Product();
        product.setId(10L);
        product.setFactory(factory);
        product.setCode("P001");
        product.setName("Test Product");
        product.setCreatedAt(LocalDateTime.now());
        return product;
    }
}
