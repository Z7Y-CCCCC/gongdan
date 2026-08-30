package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.ProductOperationLog;
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(workOrderRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被工单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByInboundReceipt() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inboundReceiptRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被入库单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByOutboundReceipt() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(outboundReceiptRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被出库单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedByBom() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(bomRepository.existsByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被BOM引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRejectsProductReferencedBySalesOrder() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.existsItemByFactoryIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, "operator", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("产品已被销售订单引用，不能删除");

        verify(productRepository, never()).delete(product);
    }

    @Test
    void deleteRemovesProductAndWritesLogWhenUnreferenced() {
        Product product = product();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        service.delete(1L, "operator", 10L);

        verify(productRepository).delete(product);
        ArgumentCaptor<ProductOperationLog> logCaptor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(productOperationLogRepository).save(logCaptor.capture());
        ProductOperationLog log = logCaptor.getValue();
        assertThat(log.getActionType()).isEqualTo("DELETE");
        assertThat(log.getProductCode()).isEqualTo("P001");
        assertThat(log.getProductName()).isEqualTo("Test Product");
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
